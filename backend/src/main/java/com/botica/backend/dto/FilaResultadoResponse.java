package com.botica.backend.dto;

import java.math.BigDecimal;

/**
 * Una fila del reporte de resultados (D-E2-3, Tarea 18).
 * <ul>
 *   <li>{@code periodo}: clave estable — {@code YYYY-MM-DD} (día), {@code YYYY-MM} (mes) o el
 *       primer día real de la semana dentro del rango (semana).</li>
 *   <li>{@code etiqueta}: texto para mostrar, armado en el servidor — {@code dd/MM/yyyy},
 *       {@code MM/yyyy} o {@code "dd/MM al dd/MM"} con las fechas reales de la semana.</li>
 *   <li>{@code gastos} = costo de ventas + mermas a costo + egresos (Bloque C, R19).</li>
 * </ul>
 * Montos en NUMERIC/BigDecimal con IGV incluido (reporte interno, sin valor tributario).
 */
public record FilaResultadoResponse(
        String periodo,
        String etiqueta,
        BigDecimal ventas,
        BigDecimal costoVentas,
        BigDecimal margenBruto,
        BigDecimal mermasCosto,
        BigDecimal egresos,
        BigDecimal gastos,
        BigDecimal resultado
) {}
