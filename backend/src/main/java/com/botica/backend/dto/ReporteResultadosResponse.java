package com.botica.backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Respuesta de GET /api/reportes/resultados (D-E2-3, solo ADMINISTRADOR).
 * Etiquetado explícito como reporte interno sin valor tributario y montos con IGV incluido.
 */
public record ReporteResultadosResponse(
        LocalDate desde,
        LocalDate hasta,
        String agrupar,
        String nota,
        List<FilaResultadoResponse> filas,
        TotalesResultadoResponse totales
) {}
