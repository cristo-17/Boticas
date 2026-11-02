package com.botica.backend.dto;

import java.math.BigDecimal;

/**
 * Una fila del reporte de resultados (por día YYYY-MM-DD o por mes YYYY-MM, D-E2-3).
 * Todos los montos en NUMERIC/BigDecimal con IGV incluido (reporte interno, sin valor tributario).
 */
public record FilaResultadoResponse(
        String periodo,
        BigDecimal ventas,
        BigDecimal costoVentas,
        BigDecimal margenBruto,
        BigDecimal mermasCosto,
        BigDecimal egresos,
        BigDecimal resultado
) {}
