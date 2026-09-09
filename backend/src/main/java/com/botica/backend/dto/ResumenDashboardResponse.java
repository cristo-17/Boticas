package com.botica.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Forma reformada (hueco 5, docs/API-CONTRATO.md): números crudos, no
 * frases ya armadas -- el frontend arma "+12% vs. ayer · 64 boletas"
 * con moneda.util.ts/Intl.DateTimeFormat. cajaEstado es del usuario
 * autenticado (su propia caja), no un agregado de la botica -- eso lo
 * cubre la alerta "Caja sin cerrar" en GET /api/alertas. Deliberadamente
 * SIN ningún monto esperado de caja (conteo ciego, hueco 2).
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
