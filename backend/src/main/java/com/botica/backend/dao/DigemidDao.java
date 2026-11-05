package com.botica.backend.dao;

import com.botica.backend.dto.FilaDigemidResponse;
import com.botica.backend.dto.TotalDigemidProductoResponse;
import com.botica.backend.dto.TotalesDigemidResponse;

import java.time.OffsetDateTime;
import java.util.List;

/** Reporte de dispensación con receta (RF-54). inicio inclusivo, fin exclusivo. */
public interface DigemidDao {

    /** limite null = todas las filas (exportación .xlsx). */
    List<FilaDigemidResponse> listarLineas(Long boticaId, OffsetDateTime inicio, OffsetDateTime fin, Integer limite, long offset);

    List<TotalDigemidProductoResponse> totalesPorProducto(Long boticaId, OffsetDateTime inicio, OffsetDateTime fin);

    TotalesDigemidResponse totales(Long boticaId, OffsetDateTime inicio, OffsetDateTime fin);
}
