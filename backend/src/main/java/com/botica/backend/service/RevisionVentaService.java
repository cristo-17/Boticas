package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.RevisionVentaDao;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ResolverRevisionRequest;
import com.botica.backend.dto.RevisionVentaResponse;
import com.botica.backend.exception.FiltroInvalidoException;
import com.botica.backend.exception.ResolucionInvalidaException;
import com.botica.backend.exception.RevisionNoEncontradaException;
import com.botica.backend.exception.RevisionYaResueltaException;
import com.botica.backend.model.RevisionVenta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Tarea 22 (D-E2-7): las revisiones de las ventas sin conexión. Solo el
 * ADMINISTRADOR (SecurityConfig, R17). VISTA para cualquier tipo; FALTANTE
 * solo para STOCK_INSUFICIENTE y con nota: las unidades se dan por faltante
 * de inventario y la línea queda sin lote ni costo para siempre.
 */
@Service
public class RevisionVentaService {

    private static final Set<String> ESTADOS = Set.of(RevisionVenta.PENDIENTE, RevisionVenta.RESUELTA);

    private final RevisionVentaDao revisionDao;
    private final ContextoOperacion contexto;

    public RevisionVentaService(RevisionVentaDao revisionDao, ContextoOperacion contexto) {
        this.revisionDao = revisionDao;
        this.contexto = contexto;
    }

    /** estado vacío = todas; si no, PENDIENTE o RESUELTA (otro valor: 400 FILTRO_INVALIDO). */
    public PaginaResponse<RevisionVentaResponse> listar(String estado, int pagina, int tamano) {
        String filtro = estado == null || estado.isBlank() ? null : estado.trim();
        if (filtro != null && !ESTADOS.contains(filtro)) {
            throw new FiltroInvalidoException("estado", filtro);
        }
        PaginaResponse<RevisionVenta> p = revisionDao.listarPaginado(contexto.boticaId(), filtro, pagina, tamano);
        return PaginaResponse.de(p.contenido().stream().map(VentaTransaccion::aRespuesta).toList(),
                p.pagina(), p.tamano(), p.totalElementos());
    }

    @Transactional
    public RevisionVentaResponse resolver(Long id, ResolverRevisionRequest request) {
        Long boticaId = contexto.boticaId();
        RevisionVenta revision = revisionDao.bloquearPorId(boticaId, id).orElseThrow(RevisionNoEncontradaException::new);
        if (!RevisionVenta.PENDIENTE.equals(revision.getEstado())) {
            throw new RevisionYaResueltaException();
        }
        String nota = request.nota() == null || request.nota().isBlank() ? null : request.nota().trim();
        if (RevisionVenta.FALTANTE.equals(request.accion())) {
            if (!RevisionVenta.STOCK_INSUFICIENTE.equals(revision.getTipo())) {
                throw new ResolucionInvalidaException("FALTANTE solo resuelve una revisión de stock insuficiente");
            }
            if (nota == null) {
                throw new ResolucionInvalidaException("Para dar las unidades por faltante escribe una nota");
            }
        }
        revisionDao.resolver(boticaId, id, request.accion(), nota, contexto.usuarioId());
        return revisionDao.bloquearPorId(boticaId, id).map(VentaTransaccion::aRespuesta)
                .orElseThrow(RevisionNoEncontradaException::new);
    }

    /** Contador de Alertas (solo ADMINISTRADOR). */
    public long contarVentasPorRevisar() {
        return revisionDao.contarVentasConPendientes(contexto.boticaId());
    }
}
