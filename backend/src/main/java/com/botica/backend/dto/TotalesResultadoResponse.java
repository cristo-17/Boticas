package com.botica.backend.dto;

import java.math.BigDecimal;

/**
 * Totales del rango del reporte de resultados calculados en el backend (R19: el frontend no suma dinero).
 * {@code gastos} = costo de ventas + mermas a costo + egresos.
 */
public record TotalesResultadoResponse(
        BigDecimal ventas,
        BigDecimal costoVentas,
        BigDecimal margenBruto,
        BigDecimal mermasCosto,
        BigDecimal egresos,
        BigDecimal gastos,
        BigDecimal resultado
) {}
