package com.botica.backend.dao;

import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.model.RevisionVenta;

import java.util.List;
import java.util.Optional;

/** Tarea 22: revisiones_venta. Todo filtra por botica_id (D1). */
public interface RevisionVentaDao {

    RevisionVenta insertar(RevisionVenta revision);

    List<RevisionVenta> listarPorVenta(Long boticaId, Long ventaId);

    /** estado null = todas. Orden: más recientes primero (creado_en DESC, id DESC); no hay orden elegible. */
    PaginaResponse<RevisionVenta> listarPaginado(Long boticaId, String estado, int pagina, int tamano);

    /** Con FOR UPDATE: dos administradores resolviendo a la vez no pisan la resolución del otro. */
    Optional<RevisionVenta> bloquearPorId(Long boticaId, Long id);

    void resolver(Long boticaId, Long id, String resolucion, String nota, Long resueltaPor);

    /** Ventas (no revisiones) con al menos una revisión PENDIENTE: el contador de Alertas. */
    long contarVentasConPendientes(Long boticaId);
}
