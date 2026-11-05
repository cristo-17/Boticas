package com.botica.backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/reportes/digemid (RF-54, solo ADMINISTRADOR). desde/hasta: días de
 * Lima. lineas va paginado (Anexo D); los totales son de todo el rango.
 */
public record ReporteDigemidResponse(
        LocalDate desde,
        LocalDate hasta,
        String nota,
        PaginaResponse<FilaDigemidResponse> lineas,
        List<TotalDigemidProductoResponse> totalesPorProducto,
        TotalesDigemidResponse totales
) {
}
