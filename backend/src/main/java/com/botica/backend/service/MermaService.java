package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dao.LoteDao;
import com.botica.backend.dao.MermaDao;
import com.botica.backend.dao.ProductoDao;
import com.botica.backend.dto.MermaResponse;
import com.botica.backend.dto.NuevaMermaRequest;
import com.botica.backend.exception.LoteNoEncontradoException;
import com.botica.backend.exception.ObservacionRequeridaException;
import com.botica.backend.exception.SinCajaAbiertaException;
import com.botica.backend.exception.StockInsuficienteMermaException;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.model.Lote;
import com.botica.backend.model.Merma;
import com.botica.backend.model.MovimientoCaja;
import com.botica.backend.util.Dinero;
import com.botica.backend.util.FechaNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.botica.backend.model.PresentacionProducto;
import java.util.Set;

/** Reglas de negocio de merma — sin SQL aquí (Regla 2). */
@Service
public class MermaService {

    private static final Set<String> MOTIVOS_CON_OBSERVACION = Set.of("Robo o pérdida", "Otro");

    private final MermaDao mermaDao;
    private final LoteDao loteDao;
    private final CajaDao cajaDao;
    private final ProductoDao productoDao;
    private final ContextoOperacion contexto;
    private final FechaNegocio fechaNegocio;

    public MermaService(MermaDao mermaDao, LoteDao loteDao, CajaDao cajaDao,
                        ProductoDao productoDao, ContextoOperacion contexto, FechaNegocio fechaNegocio) {
        this.mermaDao = mermaDao;
        this.loteDao = loteDao;
        this.cajaDao = cajaDao;
        this.productoDao = productoDao;
        this.contexto = contexto;
        this.fechaNegocio = fechaNegocio;
    }

    public List<MermaResponse> listarDelDia() {
        return mermaDao.listarDelDia(contexto.boticaId(), fechaNegocio.hoy());
    }

    @Transactional
    public MermaResponse registrar(NuevaMermaRequest request) {
        Long boticaId = contexto.boticaId();
        Long usuarioId = contexto.usuarioId();

        // 1. Caja abierta (merma registra un movimiento de caja)
        CajaDiaria caja = cajaDao.buscarAbiertaDelUsuario(boticaId, usuarioId)
                .orElseThrow(SinCajaAbiertaException::new);

        // 2. Lote existe y pertenece a la botica
        Lote lote = loteDao.buscarPorId(boticaId, request.loteId())
                .orElseThrow(LoteNoEncontradoException::new);

        // 3. Stock suficiente
        if (request.cantidad() > lote.getStock()) {
            throw new StockInsuficienteMermaException(lote.getStock());
        }

        // 4. Motivos que exigen observación
        if (MOTIVOS_CON_OBSERVACION.contains(request.motivo())
                && (request.observacion() == null || request.observacion().isBlank())) {
            throw new ObservacionRequeridaException();
        }

        // 5. Valor a precio de venta (presentación Unidad, factor_conversion=1)
        Map<Long, List<PresentacionProducto>> pMap =
                productoDao.listarPresentacionesPorProductos(List.of(lote.getProductoId()));
        BigDecimal precioUnitario = pMap.getOrDefault(lote.getProductoId(), List.of()).stream()
                .filter(p -> p.getFactorConversion() == 1)
                .findFirst()
                .map(PresentacionProducto::getPrecio)
                .orElse(BigDecimal.ZERO);
        BigDecimal valorVenta = Dinero.redondear(precioUnitario.multiply(BigDecimal.valueOf(request.cantidad())));

        // 6. Descuento de stock
        loteDao.descontarStock(lote.getId(), request.cantidad());

        // 7. Insertar merma
        Merma merma = Merma.builder()
                .boticaId(boticaId)
                .loteId(lote.getId())
                .usuarioId(usuarioId)
                .cantidad(request.cantidad())
                .motivo(request.motivo())
                .observacion(request.observacion())
                .valorVenta(valorVenta)
                .build();
        Merma creada = mermaDao.insertar(merma);

        // 8. Movimiento de stock
        mermaDao.insertarMovimientoStock(boticaId, lote.getId(), request.cantidad(), creada.getId(), usuarioId);

        // 9. Movimiento de caja (afecta_efectivo=false: la merma no mueve el cajón)
        MovimientoCaja movCaja = MovimientoCaja.builder()
                .boticaId(boticaId)
                .cajaId(caja.getId())
                .tipo("merma")
                .descripcion("Merma #" + creada.getId() + " — " + request.motivo())
                .monto(valorVenta.negate())
                .afectaEfectivo(false)
                .creadoPor(usuarioId)
                .build();
        cajaDao.insertarMovimiento(movCaja);

        // 10. Obtener nombre y código para la respuesta
        return mermaDao.listarDelDia(boticaId, fechaNegocio.hoy()).stream()
                .filter(r -> r.id().equals(creada.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Merma recién creada no se pudo releer, id=" + creada.getId()));
    }
}
