package com.botica.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Forma de docs/API-CONTRATO.md: números crudos para que el frontend
 * formatee "+12% vs. ayer · 64 boletas" según corresponda.
 */
public record ResumenDashboardResponse(
        BigDecimal ventasHoy,
        int ventasHoyVariacionPct,
        long boletasHoy,
        long productosPorVencer,
        long productosPorVencerCriticos,
        long stockCritico,
        long stockAgotado,
        String cajaEstado,
        OffsetDateTime cajaHoraApertura
) {
}
