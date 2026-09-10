package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.LoteDao;
import com.botica.backend.dao.MermaDao;
import com.botica.backend.dto.ConfigResponse;
import com.botica.backend.dto.MermaResponse;
import com.botica.backend.dto.NuevaMermaRequest;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.exception.CantidadExcedeStockException;
import com.botica.backend.exception.LoteNoEncontradoException;
import com.botica.backend.exception.MotivoInvalidoException;
import com.botica.backend.exception.ObservacionRequeridaException;
import com.botica.backend.model.Merma;
import com.botica.backend.model.MovimientoStock;
import com.botica.backend.util.Dinero;
import com.botica.backend.util.FechaNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Reglas de negocio de merma (Tarea 11 Bloque C) — sin SQL acá (Regla 2).
 * Es destructiva y sin deshacer: la cantidad topada al stock real se
 * valida ACÁ, contra el stock que acaba de leer con FOR UPDATE, nunca
 * confiando en lo que mandó el cliente ni en un botón deshabilitado.
 */
@Service
public class MermaService {

    private final MermaDao mermaDao;
    private final LoteDao loteDao;
    private final ContextoOperacion contexto;
    private final ConfigService configService;
    private final FechaNegocio fechaNegocio;

    public MermaService(MermaDao mermaDao, LoteDao loteDao, ContextoOperacion contexto, ConfigService configService, FechaNegocio fechaNegocio) {
        this.mermaDao = mermaDao;
        this.loteDao = loteDao;
        this.contexto = contexto;
        this.configService = configService;
        this.fechaNegocio = fechaNegocio;
    }

    @Transactional
    public MermaResponse registrar(NuevaMermaRequest request) {
        Long boticaId = contexto.boticaId();
        Long usuarioId = contexto.usuarioId();

        // Lock del lote elegido por el usuario -- no FEFO (es lo contrario de una venta: acá el usuario ya sabe qué lote se echó a perder).
        MermaDao.LoteParaMerma lote = mermaDao.bloquearLoteConPrecio(boticaId, request.loteId())
                .orElseThrow(LoteNoEncontradoException::new);

        ConfigResponse config = configService.obtener();
        if (!config.motivosMerma().contains(request.motivo())) {
            throw new MotivoInvalidoException(request.motivo());
        }
        if (config.motivosQueRequierenObservacion().contains(request.motivo())
                && (request.observacion() == null || request.observacion().isBlank())) {
            throw new ObservacionRequeridaException();
        }
        // La validación real: contra el stock que ACABA de leer con FOR UPDATE, no lo que mandó el cliente ni un botón deshabilitado en pantalla.
        if (request.cantidad() > lote.stock()) {
            throw new CantidadExcedeStockException(lote.stock(), request.cantidad());
        }

        BigDecimal precioUnitario = lote.precioUnitario() == null ? BigDecimal.ZERO : lote.precioUnitario();
        BigDecimal valorVenta = Dinero.redondear(precioUnitario.multiply(BigDecimal.valueOf(request.cantidad())));

        Merma merma = Merma.builder()
                .boticaId(boticaId)
                .loteId(lote.id())
                .usuarioId(usuarioId)
                .cantidad(request.cantidad())
                .motivo(request.motivo())
                .observacion(request.observacion())
                .valorVenta(valorVenta)
                .build();
        Merma creada = mermaDao.insertar(merma);

        loteDao.descontarStock(lote.id(), request.cantidad());

        // origenCaptura NULL (D4, corregido): una merma no se busca ni se escanea, se selecciona de una lista
        MovimientoStock movimiento = MovimientoStock.builder()
                .boticaId(boticaId)
                .loteId(lote.id())
                .tipo("MERMA")
                .cantidad(-request.cantidad())
                .origenCaptura(null)
                .referenciaMermaId(creada.getId())
                .creadoPor(usuarioId)
                .build();
        mermaDao.insertarMovimientoStock(movimiento);

        return new MermaResponse(creada.getId(), lote.id(), lote.productoNombre(), lote.codigo(),
                request.cantidad(), request.motivo(), request.observacion(), valorVenta, usuarioId, creada.getFecha());
    }

    public PaginaResponse<MermaResponse> listarDelDia(int pagina, int tamano, String orden) {
        return mermaDao.listarPaginado(contexto.boticaId(), fechaNegocio.hoy(), pagina, tamano, orden);
    }
}
