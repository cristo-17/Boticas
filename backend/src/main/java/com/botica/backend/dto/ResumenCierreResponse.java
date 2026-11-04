package com.botica.backend.dto;

import java.math.BigDecimal;

/**
 * GET /api/caja/{id}/resumen-cierre — se llama ANTES de contar.
 * Deliberadamente NO tiene montoEsperado/diferencia/semaforoDescuadre:
 * conteo ciego (hueco 2, docs/DECISIONES.md). Esos tres campos solo
 * existen en CajaResponse, y solo se llenan tras POST /api/caja/cerrar.
 * Tampoco tiene totalVentasEfectivo (DIF-10, Tarea 0B): sumado a la
 * apertura es casi el esperado. El digital sí, porque no se cuenta.
 * Tarea 22: cantidadVentasSinConexion es cuántas de cantidadVentas llegaron
 * desde la cola sin conexión. Solo la cantidad, nunca su monto.
 */
public record ResumenCierreResponse(
        BigDecimal totalVentasDigital,
        long cantidadVentas,
        long cantidadMovimientos,
        long cantidadVentasSinConexion
) {
    public ResumenCierreResponse(BigDecimal totalVentasDigital, long cantidadVentas, long cantidadMovimientos) {
        this(totalVentasDigital, cantidadVentas, cantidadMovimientos, 0L);
    }
}
