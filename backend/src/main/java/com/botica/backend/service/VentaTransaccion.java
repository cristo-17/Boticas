package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dao.LoteDao;
import com.botica.backend.dao.ProductoDao;
import com.botica.backend.dao.RevisionVentaDao;
import com.botica.backend.dao.VentaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.ItemVentaResponse;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.RecetaRequest;
import com.botica.backend.dto.RecetaResponse;
import com.botica.backend.dto.RevisionVentaResponse;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.exception.CajaNoEncontradaException;
import com.botica.backend.exception.PrecioCobradoRequeridoException;
import com.botica.backend.exception.PresentacionInvalidaException;
import com.botica.backend.exception.ProductoInactivoException;
import com.botica.backend.exception.RecetaInvalidaException;
import com.botica.backend.exception.RecetaRequeridaException;
import com.botica.backend.exception.SinCajaAbiertaException;
import com.botica.backend.exception.SinPermisoException;
import com.botica.backend.exception.StockInsuficienteException;
import com.botica.backend.exception.StockSoloVencidoException;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.model.Lote;
import com.botica.backend.model.MovimientoCaja;
import com.botica.backend.model.MovimientoStock;
import com.botica.backend.model.PresentacionProducto;
import com.botica.backend.model.Producto;
import com.botica.backend.model.Receta;
import com.botica.backend.model.RevisionVenta;
import com.botica.backend.model.Venta;
import com.botica.backend.model.VentaDetalle;
import com.botica.backend.event.StockCriticoEvent;
import com.botica.backend.util.Dinero;
import com.botica.backend.util.FechaNegocio;
import com.botica.backend.util.ReglasVenta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * El cuerpo transaccional real de registrar una venta (Tarea 11
 * Bloque B) — separado de {@link VentaService} a propósito: si dos
 * peticiones con la misma claveIdempotencia chocan en el INSERT de
 * ventas, VentaService necesita que ESTA transacción ya haya hecho
 * rollback DE VERDAD (stock incluido) antes de volver a consultar. Un
 * catch dentro del mismo método no alcanza — Postgres deja la
 * transacción "abortada" tras un statement fallido, y solo un rollback
 * real (la excepción propagándose hasta el proxy de Spring) la libera.
 * Auto-invocación (this.ejecutar desde el propio VentaService) no
 * dispara el proxy transaccional de Spring — por eso es un bean aparte.
 *
 * Tarea 22 (D-E2-7, docs/DISENO-OFFLINE.md): con request.sinConexion la
 * venta ya ocurrió en el mostrador. Nunca se rechaza por el negocio: cada
 * problema (stock, precio, caja cerrada, producto inactivo, receta) queda
 * como una fila de revisiones_venta. Solo un cuerpo mal formado (400) o una
 * caja ajena (403) la rechazan. La hora de la venta es la del servidor al
 * recibirla (ventas.fecha DEFAULT now()); horaDispositivo es solo auditoría.
 */
@Component
class VentaTransaccion {

    private final VentaDao ventaDao;
    private final LoteDao loteDao;
    private final ProductoDao productoDao;
    private final CajaDao cajaDao;
    private final ContextoOperacion contexto;
    private final ConfigNegocioProperties config;
    private final FechaNegocio fechaNegocio;
    private final ApplicationEventPublisher eventPublisher;
    private final RevisionVentaDao revisionDao;

    VentaTransaccion(VentaDao ventaDao, LoteDao loteDao, ProductoDao productoDao, CajaDao cajaDao,
                     ContextoOperacion contexto, ConfigNegocioProperties config, FechaNegocio fechaNegocio) {
        this(ventaDao, loteDao, productoDao, cajaDao, contexto, config, fechaNegocio, null, null);
    }

    @Autowired
    VentaTransaccion(VentaDao ventaDao, LoteDao loteDao, ProductoDao productoDao, CajaDao cajaDao,
                     ContextoOperacion contexto, ConfigNegocioProperties config, FechaNegocio fechaNegocio,
                     @Autowired(required = false) ApplicationEventPublisher eventPublisher,
                     @Autowired(required = false) RevisionVentaDao revisionDao) {
        this.ventaDao = ventaDao;
        this.loteDao = loteDao;
        this.productoDao = productoDao;
        this.cajaDao = cajaDao;
        this.contexto = contexto;
        this.config = config;
        this.fechaNegocio = fechaNegocio;
        this.eventPublisher = eventPublisher;
        this.revisionDao = revisionDao;
    }

    @Transactional
    VentaResponse ejecutar(NuevaVentaRequest request) {
        Long boticaId = contexto.boticaId();
        Long usuarioId = contexto.usuarioId();

        // Paso 1: idempotencia -- ANTES de cualquier otra validación o mutación.
        // Tarea 22: también para una venta sin conexión reenviada horas después.
        Optional<Venta> existente = ventaDao.buscarPorClaveIdempotencia(boticaId, request.claveIdempotencia());
        if (existente.isPresent()) {
            return construirRespuestaDesdeVentaExistente(existente.get());
        }

        boolean sinConexion = request.sinConexion() != null;
        // Problemas de una venta sin conexión; se insertan después de la cabecera (necesitan venta_id).
        List<RevisionVenta> revisiones = new ArrayList<>();

        // Paso 2: caja. En línea, la abierta del usuario. Sin conexión, la caja en la que se vendió.
        CajaDiaria caja = sinConexion
                ? cajaDeLaVentaSinConexion(boticaId, usuarioId, request.sinConexion().cajaId(), revisiones)
                : cajaDao.buscarAbiertaDelUsuario(boticaId, usuarioId).orElseThrow(SinCajaAbiertaException::new);

        BigDecimal totalVenta = BigDecimal.ZERO;
        List<ItemVentaResponse> itemsRespuesta = new ArrayList<>();
        List<VentaDetalle> detallesAInsertar = new ArrayList<>();
        // La revisión STOCK_INSUFICIENTE apunta a su línea sin lote: se enlazan al insertar el detalle.
        Map<VentaDetalle, RevisionVenta> revisionPorLineaSinLote = new IdentityHashMap<>();
        // Tarea 20 (D-E2-4): productos del carrito que solo se venden con receta.
        List<String> conReceta = new ArrayList<>();
        LocalDate hoy = fechaNegocio.hoy();

        for (ItemVentaRequest item : request.items()) {
            if (sinConexion && item.precioCobrado() == null) {
                throw new PrecioCobradoRequeridoException();
            }
            Producto producto = productoDao.obtenerPorId(boticaId, item.productoId())
                    .orElseThrow(PresentacionInvalidaException::new);
            if (!Boolean.TRUE.equals(producto.getActivo())) {
                // Tarea 17: desactivado no se vende, aunque un carrito viejo todavía lo tenga.
                if (!sinConexion) {
                    throw new ProductoInactivoException(producto.getNombre());
                }
                revisiones.add(revision(RevisionVenta.OTRO,
                        producto.getNombre() + " estaba desactivado cuando llegó la venta sin conexión"));
            }
            if (producto.isRequiereReceta() && !conReceta.contains(producto.getNombre())) {
                conReceta.add(producto.getNombre());
            }
            PresentacionProducto presentacion = productoDao.obtenerPresentacion(boticaId, item.productoId(), item.presentacionId())
                    .orElseThrow(PresentacionInvalidaException::new);
            if (!Boolean.TRUE.equals(presentacion.getActivo())) {
                // Una presentación quitada no se vende en línea; sin conexión ya se vendió (existe y tiene su factor).
                if (!sinConexion) {
                    throw new PresentacionInvalidaException();
                }
                revisiones.add(revision(RevisionVenta.OTRO, "La presentación " + presentacion.getEtiqueta() + " de "
                        + producto.getNombre() + " estaba desactivada cuando llegó la venta sin conexión"));
            }

            int cantidadPresentacion = item.cantidad();

            // Tarea 22 (D-E2-7): sin conexión se respeta el precio que cobró el cajero.
            BigDecimal precioPresentacion = presentacion.getPrecio();
            if (sinConexion) {
                precioPresentacion = item.precioCobrado();
                if (precioPresentacion.compareTo(presentacion.getPrecio()) != 0) {
                    RevisionVenta r = revision(RevisionVenta.PRECIO_DISTINTO, "Se cobró " + presentacion.getEtiqueta() + " de "
                            + producto.getNombre() + " con el precio que tenía el dispositivo, distinto del vigente");
                    r.setPrecioCobrado(precioPresentacion);
                    r.setPrecioVigente(presentacion.getPrecio());
                    revisiones.add(r);
                }
            }

            // Paso 3 + Anexo C regla 6: se redondea el TOTAL de línea, nunca el precio unitario primero (ReglasVenta).
            ReglasVenta.Linea linea = ReglasVenta.calcularLinea(
                    precioPresentacion, cantidadPresentacion, presentacion.getFactorConversion());
            int cantidadBaseTotal = linea.cantidadBase();
            BigDecimal totalLineaFinal = linea.totalLinea();
            BigDecimal precioUnitarioBase = linea.precioUnitarioBase();

            // Paso 5: lock FEFO, sin lotes vencidos. Paso 4: consumir en ese orden, repartiendo si hace falta.
            // Tarea 22: FEFO decide con el día de recepción (límite aceptado: un lote que venció entre la
            // venta y el envío no se consume; esas unidades van a la línea sin lote).
            List<Lote> lotesBloqueados = loteDao.bloquearLotesFefo(boticaId, item.productoId(), hoy);
            int disponibleTotal = lotesBloqueados.stream().mapToInt(Lote::getStock).sum();
            if (!sinConexion) {
                if (disponibleTotal == 0 && loteDao.sumarStockVencido(boticaId, item.productoId(), hoy) > 0) {
                    // DIF-13: "disponible 0" confunde al cajero que ve el producto en el estante.
                    throw new StockSoloVencidoException(producto.getNombre());
                }
                if (disponibleTotal < cantidadBaseTotal) {
                    // Paso 6: rollback completo -- lanzar acá basta, @Transactional revierte todo lo ya insertado en este método.
                    throw new StockInsuficienteException(producto.getNombre(), disponibleTotal, cantidadBaseTotal);
                }
            }

            List<Lote> lotesUsados = new ArrayList<>();
            List<Integer> cantidadesConsumidas = new ArrayList<>();
            int restante = cantidadBaseTotal;
            for (Lote lote : lotesBloqueados) {
                if (restante <= 0) break;
                int consumir = Math.min(restante, lote.getStock());
                lotesUsados.add(lote);
                cantidadesConsumidas.add(consumir);
                restante -= consumir;
            }
            // Tarea 22: lo que falta (solo puede quedar sin conexión) es la línea sin lote ni costo (conflicto 5a).
            int faltante = restante;
            if (faltante > 0) {
                cantidadesConsumidas.add(faltante);
            }

            // La última fila se lleva el resto exacto: la suma de filas es EXACTA al total de línea (ReglasVenta).
            List<BigDecimal> totalesFila = ReglasVenta.repartirTotal(totalLineaFinal, cantidadesConsumidas, cantidadBaseTotal);
            for (int i = 0; i < cantidadesConsumidas.size(); i++) {
                boolean sinLote = i >= lotesUsados.size();
                Lote lote = sinLote ? null : lotesUsados.get(i);
                int consumido = cantidadesConsumidas.get(i);

                // Paso 7: precio y costo CONGELADOS -- nunca una referencia al precio/costo actual.
                VentaDetalle detalle = VentaDetalle.builder()
                        .boticaId(boticaId)
                        .productoId(item.productoId())
                        .presentacionId(item.presentacionId())
                        .loteId(sinLote ? null : lote.getId())
                        .nombreProducto(producto.getNombre())
                        .etiquetaPresentacion(presentacion.getEtiqueta())
                        .cantidad(consumido)
                        .precioUnitario(precioUnitarioBase)
                        .costoUnitario(sinLote ? null : lote.getCostoUnitario())
                        .totalLinea(totalesFila.get(i))
                        .origenCaptura(item.origenCaptura())
                        // Tarea 20: congelado como el precio y el costo; el Kardex clasifica con esto.
                        .requiereReceta(producto.isRequiereReceta())
                        .build();
                detallesAInsertar.add(detalle);

                if (sinLote) {
                    RevisionVenta r = revision(RevisionVenta.STOCK_INSUFICIENTE, "Faltaron " + consumido + " unidades vendibles de "
                            + producto.getNombre() + " al registrar la venta sin conexión (había " + disponibleTotal + ")");
                    r.setUnidadesFaltantes(consumido);
                    revisionPorLineaSinLote.put(detalle, r);
                } else {
                    loteDao.descontarStock(lote.getId(), consumido);
                }
            }

            int stockRestante = Math.max(disponibleTotal - cantidadBaseTotal, 0);
            if (ReglasVenta.dejaStockCritico(stockRestante, config.getUmbralStockBajo()) && eventPublisher != null) {
                eventPublisher.publishEvent(new StockCriticoEvent(boticaId, item.productoId(), producto.getNombre(), stockRestante));
            }

            totalVenta = totalVenta.add(totalLineaFinal);
            itemsRespuesta.add(new ItemVentaResponse(item.productoId(), item.presentacionId(), producto.getNombre(),
                    presentacion.getEtiqueta(), precioPresentacion, cantidadPresentacion, item.origenCaptura()));
        }

        // Tarea 20: con al menos una línea que requiere receta, la receta es obligatoria.
        // Se valida antes de insertar la cabecera; el stock ya descontado se revierte con la excepción.
        Receta receta = validarReceta(request, conReceta, sinConexion, revisiones);

        // Anexo C regla 7: el IGV se EXTRAE del total ya cobrado, nunca se suma encima.
        BigDecimal igvTasa = config.getIgv();
        BigDecimal total = Dinero.redondear(totalVenta);
        Dinero.DesgloseIgv desglose = Dinero.extraerIgv(total, igvTasa);
        BigDecimal subtotal = desglose.subtotal();
        BigDecimal igv = desglose.igv();

        Venta venta = Venta.builder()
                .boticaId(boticaId)
                .usuarioId(usuarioId)
                .cajaId(caja.getId())
                .subtotal(subtotal)
                .igv(igv)
                .igvTasa(igvTasa)
                .total(total)
                .metodoPago(request.metodoPago())
                .claveIdempotencia(request.claveIdempotencia())
                .sincronizada(true)
                .vendidaSinConexion(sinConexion)
                .horaDispositivo(sinConexion ? request.sinConexion().horaDispositivo() : null)
                .build();
        // Sin try/catch acá a propósito: si el INSERT viola la unicidad de
        // claveIdempotencia (dos peticiones concurrentes con la misma clave),
        // la excepción tiene que PROPAGARSE sin atraparla -- es la única forma
        // de que Spring haga un rollback real (stock ya descontado incluido)
        // antes de que VentaService vuelva a consultar por esa clave.
        Venta creada = ventaDao.insertarCabecera(venta);

        // Paso 8: detalle + movimiento de stock por cada lote consumido (la línea sin lote no mueve stock).
        for (VentaDetalle detalle : detallesAInsertar) {
            detalle.setVentaId(creada.getId());
            ventaDao.insertarDetalle(detalle);
            RevisionVenta deLaLinea = revisionPorLineaSinLote.get(detalle);
            if (deLaLinea != null) {
                deLaLinea.setVentaDetalleId(detalle.getId());
                revisiones.add(deLaLinea);
                continue;
            }
            MovimientoStock movimientoStock = MovimientoStock.builder()
                    .boticaId(boticaId)
                    .loteId(detalle.getLoteId())
                    .tipo("VENTA")
                    .cantidad(-detalle.getCantidad())
                    .origenCaptura(detalle.getOrigenCaptura())
                    .referenciaVentaId(creada.getId())
                    .creadoPor(usuarioId)
                    .build();
            ventaDao.insertarMovimientoStock(movimientoStock);
        }

        if (receta != null) {
            receta.setBoticaId(boticaId);
            receta.setVentaId(creada.getId());
            receta.setCreadoPor(usuarioId);
            ventaDao.insertarReceta(receta);
        }

        // Un solo movimiento de caja para toda la venta -- afecta_efectivo=false en Yape/tarjeta (Tarea 8).
        // Tarea 22: también en una caja ya cerrada (5c): su cierre no se recalcula; la revisión lo explica.
        MovimientoCaja movimientoCaja = MovimientoCaja.builder()
                .boticaId(boticaId)
                .cajaId(caja.getId())
                .tipo("venta")
                .descripcion("Venta #" + creada.getId())
                .monto(total)
                .afectaEfectivo("efectivo".equals(request.metodoPago()))
                .creadoPor(usuarioId)
                .build();
        cajaDao.insertarMovimiento(movimientoCaja);

        List<RevisionVentaResponse> revisionesGuardadas = new ArrayList<>();
        for (RevisionVenta r : revisiones) {
            r.setBoticaId(boticaId);
            r.setVentaId(creada.getId());
            r.setCreadoPor(usuarioId);
            revisionesGuardadas.add(aRespuesta(revisionDao.insertar(r)));
        }

        return new VentaResponse(creada.getId(), creada.getFecha(), usuarioId, itemsRespuesta,
                subtotal, igv, total, request.metodoPago(), true, request.claveIdempotencia(),
                receta == null ? null : RecetaResponse.desde(receta),
                creada.isVendidaSinConexion(), creada.getHoraDispositivo(), revisionesGuardadas);
    }

    /**
     * Tarea 22: la caja en la que se cobró sin conexión. Tiene que ser de esta botica (si no, 404,
     * igual que una inexistente) y de quien envía (si no, 403: una venta nunca se atribuye a otro).
     * Cerrada: la venta va igual a esa caja, que es donde estaba el efectivo cuando se contó (5c).
     */
    private CajaDiaria cajaDeLaVentaSinConexion(Long boticaId, Long usuarioId, Long cajaId, List<RevisionVenta> revisiones) {
        CajaDiaria caja = cajaDao.buscarPorId(boticaId, cajaId).orElseThrow(CajaNoEncontradaException::new);
        if (!caja.getUsuarioId().equals(usuarioId)) {
            throw new SinPermisoException();
        }
        if (!caja.estaAbierta()) {
            String diferencia = caja.getDiferencia() == null ? "" : " (su cierre tuvo una diferencia de S/ "
                    + caja.getDiferencia().toPlainString() + ")";
            revisiones.add(revision(RevisionVenta.CAJA_CERRADA, "La venta llegó después del cierre de su caja del "
                    + caja.getFecha() + ", turno " + caja.getTurno() + diferencia
                    + ". El cierre no se recalcula: esta venta explica esa diferencia"));
        }
        return caja;
    }

    private static RevisionVenta revision(String tipo, String detalle) {
        String recortado = detalle.length() > 300 ? detalle.substring(0, 300) : detalle;
        return RevisionVenta.builder().tipo(tipo).detalle(recortado).build();
    }

    static RevisionVentaResponse aRespuesta(RevisionVenta r) {
        return new RevisionVentaResponse(r.getId(), r.getVentaId(), r.getTipo(), r.getDetalle(), r.getPrecioCobrado(),
                r.getPrecioVigente(), r.getUnidadesFaltantes(), r.getEstado(), r.getResolucion(), r.getNotaResolucion(),
                r.getCreadoEn(), r.getResueltaEn(), r.getResueltaPorNombre(), r.getVentaFecha(), r.getVentaTotal(),
                r.getVendedorNombre());
    }

    /**
     * D-E2-4: número y CMP obligatorios (formato en el DTO), fecha de emisión no
     * futura (fecha de Lima). Sin ninguna línea que la requiera, la receta que
     * llegue se ignora: no se guarda una receta que nada respalda.
     * Tarea 22: sin conexión, ni la falta de receta ni la fecha futura rechazan
     * la venta (ya se dispensó): quedan como revisión OTRO. Sin receta guardada,
     * el Kardex la muestra como salida libre, que es lo que pasó.
     */
    private Receta validarReceta(NuevaVentaRequest request, List<String> conReceta, boolean sinConexion,
                                 List<RevisionVenta> revisiones) {
        if (conReceta.isEmpty()) {
            return null;
        }
        RecetaRequest datos = request.receta();
        if (datos == null) {
            if (!sinConexion) {
                throw new RecetaRequeridaException(String.join(", ", conReceta));
            }
            revisiones.add(revision(RevisionVenta.OTRO, "Venta sin conexión sin receta de "
                    + String.join(", ", conReceta) + " (el dispositivo no lo tenía marcado con receta)"));
            return null;
        }
        if (datos.fechaEmision() != null && datos.fechaEmision().isAfter(fechaNegocio.hoy())) {
            if (!sinConexion) {
                throw new RecetaInvalidaException("La fecha de emisión de la receta no puede ser futura");
            }
            revisiones.add(revision(RevisionVenta.OTRO, "La receta " + datos.numero().trim()
                    + " tiene fecha de emisión posterior al día en que llegó la venta"));
        }
        String medico = datos.medicoNombre() == null || datos.medicoNombre().isBlank() ? null : datos.medicoNombre().trim();
        return Receta.builder()
                .numero(datos.numero().trim())
                .cmp(datos.cmp().trim())
                .medicoNombre(medico)
                .fechaEmision(datos.fechaEmision())
                .build();
    }

    /**
     * Reconstruye la respuesta de una venta ya registrada (replay de
     * idempotencia, secuencial o tras perder una carrera de INSERT)
     * agregando las filas de venta_detalle de vuelta a como el cajero
     * armó el carrito -- FEFO pudo haber repartido una línea entre
     * varios lotes, la respuesta no expone esa mecánica interna.
     */
    VentaResponse construirRespuestaDesdeVentaExistente(Venta venta) {
        List<VentaDetalle> detalles = ventaDao.listarDetallePorVenta(venta.getBoticaId(), venta.getId());
        Map<String, List<VentaDetalle>> agrupados = new LinkedHashMap<>();
        for (VentaDetalle d : detalles) {
            String clave = d.getPresentacionId() + "::" + d.getOrigenCaptura();
            agrupados.computeIfAbsent(clave, k -> new ArrayList<>()).add(d);
        }
        List<ItemVentaResponse> items = new ArrayList<>();
        for (List<VentaDetalle> grupo : agrupados.values()) {
            VentaDetalle primero = grupo.get(0);
            int cantidadBaseTotal = grupo.stream().mapToInt(VentaDetalle::getCantidad).sum();
            PresentacionProducto presentacion = productoDao
                    .obtenerPresentacion(venta.getBoticaId(), primero.getProductoId(), primero.getPresentacionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "La presentación de una venta ya registrada ya no existe, presentacionId=" + primero.getPresentacionId()));
            int cantidadPresentacion = cantidadBaseTotal / presentacion.getFactorConversion();
            items.add(new ItemVentaResponse(primero.getProductoId(), primero.getPresentacionId(), primero.getNombreProducto(),
                    primero.getEtiquetaPresentacion(), presentacion.getPrecio(), cantidadPresentacion, primero.getOrigenCaptura()));
        }
        RecetaResponse receta = ventaDao.buscarReceta(venta.getBoticaId(), venta.getId())
                .map(RecetaResponse::desde).orElse(null);
        List<RevisionVentaResponse> revisiones = !venta.isVendidaSinConexion() || revisionDao == null
                ? List.of()
                : revisionDao.listarPorVenta(venta.getBoticaId(), venta.getId()).stream().map(VentaTransaccion::aRespuesta).toList();
        return new VentaResponse(venta.getId(), venta.getFecha(), venta.getUsuarioId(), items,
                venta.getSubtotal(), venta.getIgv(), venta.getTotal(), venta.getMetodoPago(),
                venta.isSincronizada(), venta.getClaveIdempotencia(), receta,
                venta.isVendidaSinConexion(), venta.getHoraDispositivo(), revisiones);
    }
}
