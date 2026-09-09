package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.LoteDao;
import com.botica.backend.dto.LoteResponse;
import com.botica.backend.dto.NuevoLoteRequest;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.exception.ProductoNoEncontradoException;
import com.botica.backend.model.Lote;
import com.botica.backend.util.FechaNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reglas de negocio de lotes — sin SQL acá (Regla 2). */
@Service
public class LoteService {

    private final LoteDao loteDao;
    private final ContextoOperacion contexto;
    private final FechaNegocio fechaNegocio;
    private final ConfigNegocioProperties config;

    public LoteService(LoteDao loteDao, ContextoOperacion contexto, FechaNegocio fechaNegocio, ConfigNegocioProperties config) {
        this.loteDao = loteDao;
        this.contexto = contexto;
        this.fechaNegocio = fechaNegocio;
        this.config = config;
    }

    public PaginaResponse<LoteResponse> listar(
            String categoria, String vencimiento, boolean soloStockBajo, Long productoId, int pagina, int tamano, String orden
    ) {
        return loteDao.listarPaginado(
                contexto.boticaId(), categoria, vencimiento, soloStockBajo, productoId, pagina, tamano, orden,
                fechaNegocio.hoy(), config.getVencimientoCriticoDias(), config.getVencimientoAdvertenciaDias(), config.getUmbralStockBajo());
    }

    @Transactional
    public LoteResponse registrar(NuevoLoteRequest request) {
        Long boticaId = contexto.boticaId();
        if (!loteDao.existeProducto(boticaId, request.productoId())) {
            throw new ProductoNoEncontradoException();
        }
        Lote lote = Lote.builder()
                .boticaId(boticaId)
                .productoId(request.productoId())
                .codigo(request.codigo())
                .fechaVencimiento(request.fechaVencimiento())
                .stock(request.stock())
                .ubicacion(request.ubicacion())
                .costoUnitario(request.costoUnitario())
                .creadoPor(contexto.usuarioId())
                .build();
        Lote creado = loteDao.insertar(lote);
        return loteDao.buscarResponsePorId(
                        boticaId, creado.getId(), fechaNegocio.hoy(),
                        config.getVencimientoCriticoDias(), config.getVencimientoAdvertenciaDias(), config.getUmbralStockBajo())
                .orElseThrow(() -> new IllegalStateException("El lote recién creado no se pudo releer, id=" + creado.getId()));
    }
}
