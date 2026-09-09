package com.botica.backend.dto;

import java.math.BigDecimal;

/**
 * GET /api/caja/{id}/resumen-cierre — se llama ANTES de contar.
 * Deliberadamente NO tiene montoEsperado/diferencia/semaforoDescuadre:
 * conteo ciego (hueco 2, docs/DECISIONES.md). Esos tres campos solo
 * existen en CajaResponse, y solo se llenan tras POST /api/caja/cerrar.
 */
public record ResumenCierreResponse(
        BigDecimal totalVentasEfectivo,
        BigDecimal totalVentasDigital,
        long cantidadVentas,
        long cantidadMovimientos
) {
}
