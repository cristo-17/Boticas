package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dao.LoteDao;
import com.botica.backend.dao.ProductoDao;
import com.botica.backend.dao.VentaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.ItemVentaResponse;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.exception.PresentacionInvalidaException;
import com.botica.backend.exception.SinCajaAbiertaException;
import com.botica.backend.exception.StockInsuficienteException;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.model.Lote;
import com.botica.backend.model.MovimientoCaja;
import com.botica.backend.model.MovimientoStock;
import com.botica.backend.model.PresentacionProducto;
import com.botica.backend.model.Producto;
import com.botica.backend.model.Venta;
import com.botica.backend.model.VentaDetalle;
import com.botica.backend.event.StockCriticoEvent;
import com.botica.backend.util.Dinero;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
 */
@Component
class VentaTransaccion {

    private static final int ESCALA_INTERMEDIA = 10;

    private final VentaDao ventaDao;
    private final LoteDao loteDao;
    private final ProductoDao productoDao;
    private final CajaDao cajaDao;
    private final ContextoOperacion contexto;
    private final ConfigNegocioProperties config;
    private final ApplicationEventPublisher eventPublisher;

    VentaTransaccion(VentaDao ventaDao, LoteDao loteDao, ProductoDao productoDao, CajaDao cajaDao,
                     ContextoOperacion contexto, ConfigNegocioProperties config) {
        this(ventaDao, loteDao, productoDao, cajaDao, contexto, config, null);
    }

    @Autowired
    VentaTransaccion(VentaDao ventaDao, LoteDao loteDao, ProductoDao productoDao, CajaDao cajaDao,
                     ContextoOperacion contexto, ConfigNegocioProperties config,
                     @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        this.ventaDao = ventaDao;
        this.loteDao = loteDao;
        this.productoDao = productoDao;
        this.cajaDao = cajaDao;
        this.contexto = contexto;
        this.config = config;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    VentaResponse ejecutar(NuevaVentaRequest request) {
        Long boticaId = contexto.boticaId();
        Long usuarioId = contexto.usuarioId();

        // Paso 1: idempotencia -- ANTES de cualquier otra validación o mutación.
        Optional<Venta> existente = ventaDao.buscarPorClaveIdempotencia(boticaId, request.claveIdempotencia());
        if (existente.isPresent()) {
            return construirRespuestaDesdeVentaExistente(existente.get());
        }

        // Paso 2: caja abierta.
        CajaDiaria caja = cajaDao.buscarAbiertaDelUsuario(boticaId, usuarioId)
                .orElseThrow(SinCajaAbiertaException::new);

        BigDecimal totalVenta = BigDecimal.ZERO;
        List<ItemVentaResponse> itemsRespuesta = new ArrayList<>();
        List<VentaDetalle> detallesAInsertar = new ArrayList<>();

        for (ItemVentaRequest item : request.items()) {
            Producto producto = productoDao.obtenerPorId(boticaId, item.productoId())
                    .orElseThrow(PresentacionInvalidaException::new);
            PresentacionProducto presentacion = productoDao.obtenerPresentacion(boticaId, item.productoId(), item.presentacionId())
                    .orElseThrow(PresentacionInvalidaException::new);

            int cantidadPresentacion = item.cantidad();
            int factorConversion = presentacion.getFactorConversion();
            int cantidadBaseTotal = cantidadPresentacion * factorConversion;

            // Paso 3 + Anexo C regla 6: se redondea el TOTAL de línea, nunca el precio unitario primero.
            BigDecimal totalLineaFinal = Dinero.redondear(
                    presentacion.getPrecio().multiply(BigDecimal.valueOf(cantidadPresentacion)));
            BigDecimal precioUnitarioBase = Dinero.redondear(
                    presentacion.getPrecio().divide(BigDecimal.valueOf(factorConversion), ESCALA_INTERMEDIA, RoundingMode.HALF_UP));

            // Paso 5: lock FEFO. Paso 4: consumir en ese orden, repartiendo si hace falta.
            List<Lote> lotesBloqueados = loteDao.bloquearLotesFefo(boticaId, item.productoId());
            int disponibleTotal = lotesBloqueados.stream().mapToInt(Lote::getStock).sum();
            if (disponibleTotal < cantidadBaseTotal) {
                // Paso 6: rollback completo -- lanzar acá basta, @Transactional revierte todo lo ya insertado en este método.
                throw new StockInsuficienteException(producto.getNombre(), disponibleTotal, cantidadBaseTotal);
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

            BigDecimal totalAsignado = BigDecimal.ZERO;
            int n = lotesUsados.size();
            for (int i = 0; i < n; i++) {
                Lote lote = lotesUsados.get(i);
                int consumido = cantidadesConsumidas.get(i);
                BigDecimal totalFila;
                if (i == n - 1) {
                    // la última fila se lleva el resto exacto -- garantiza que la suma de filas sea EXACTA al total de línea, sin arrastre de redondeo.
                    totalFila = totalLineaFinal.subtract(totalAsignado);
                } else {
                    BigDecimal proporcion = BigDecimal.valueOf(consumido)
                            .divide(BigDecimal.valueOf(cantidadBaseTotal), ESCALA_INTERMEDIA, RoundingMode.HALF_UP);
                    totalFila = Dinero.redondear(totalLineaFinal.multiply(proporcion));
                    totalAsignado = totalAsignado.add(totalFila);
                }

                // Paso 7: precio y costo CONGELADOS -- nunca una referencia al precio/costo actual.
                VentaDetalle detalle = VentaDetalle.builder()
                        .boticaId(boticaId)
                        .productoId(item.productoId())
                        .presentacionId(item.presentacionId())
                        .loteId(lote.getId())
                        .nombreProducto(producto.getNombre())
                        .etiquetaPresentacion(presentacion.getEtiqueta())
                        .cantidad(consumido)
                        .precioUnitario(precioUnitarioBase)
                        .costoUnitario(lote.getCostoUnitario())
                        .totalLinea(totalFila)
                        .origenCaptura(item.origenCaptura())
                        .build();
                detallesAInsertar.add(detalle);

                loteDao.descontarStock(lote.getId(), consumido);
            }

            int stockRestante = disponibleTotal - cantidadBaseTotal;
            if (stockRestante <= config.getUmbralStockBajo() && eventPublisher != null) {
                eventPublisher.publishEvent(new StockCriticoEvent(boticaId, item.productoId(), producto.getNombre(), stockRestante));
            }

            totalVenta = totalVenta.add(totalLineaFinal);
            itemsRespuesta.add(new ItemVentaResponse(item.productoId(), item.presentacionId(), producto.getNombre(),
                    presentacion.getEtiqueta(), presentacion.getPrecio(), cantidadPresentacion, item.origenCaptura()));
        }

        // Anexo C regla 7: el IGV se EXTRAE del total ya cobrado, nunca se suma encima.
        BigDecimal igvTasa = config.getIgv();
        BigDecimal total = Dinero.redondear(totalVenta);
        BigDecimal subtotal = Dinero.redondear(total.divide(BigDecimal.ONE.add(igvTasa), ESCALA_INTERMEDIA, RoundingMode.HALF_UP));
        BigDecimal igv = total.subtract(subtotal);

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
                .build();
        // Sin try/catch acá a propósito: si el INSERT viola la unicidad de
        // claveIdempotencia (dos peticiones concurrentes con la misma clave),
        // la excepción tiene que PROPAGARSE sin atraparla -- es la única forma
        // de que Spring haga un rollback real (stock ya descontado incluido)
        // antes de que VentaService vuelva a consultar por esa clave.
        Venta creada = ventaDao.insertarCabecera(venta);

        // Paso 8: detalle + movimiento de stock por cada lote consumido.
        for (VentaDetalle detalle : detallesAInsertar) {
            detalle.setVentaId(creada.getId());
            ventaDao.insertarDetalle(detalle);
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

        // Un solo movimiento de caja para toda la venta -- afecta_efectivo=false en Yape/tarjeta (Tarea 8).
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

        return new VentaResponse(creada.getId(), creada.getFecha(), usuarioId, itemsRespuesta,
                subtotal, igv, total, request.metodoPago(), true, request.claveIdempotencia());
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
        return new VentaResponse(venta.getId(), venta.getFecha(), venta.getUsuarioId(), items,
                venta.getSubtotal(), venta.getIgv(), venta.getTotal(), venta.getMetodoPago(),
                venta.isSincronizada(), venta.getClaveIdempotencia());
    }
}
