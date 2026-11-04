package com.botica.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Forma de docs/API-CONTRATO.md: números crudos para que el frontend
 * formatee "+12% vs. ayer · 64 boletas" según corresponda.
 * Tarea 18: ventasHoy y ventasHoyVariacionPct van en null para TECNICO
 * (con un solo cajero, el total vendido del día es casi el efectivo esperado
 * y rompería el conteo ciego, igual que "Total cobrado" en historial de ventas).
 */
public record ResumenDashboardResponse(
        BigDecimal ventasHoy,
        Integer ventasHoyVariacionPct,
        long boletasHoy,
        long productosPorVencer,
        long productosPorVencerCriticos,
        long stockCritico,
        long stockAgotado,
        String cajaEstado,
        OffsetDateTime cajaHoraApertura,
        /** Tarea 22: ventas sin conexión con revisiones pendientes. Solo ADMINISTRADOR (las resuelve); null para TECNICO. */
        Long ventasPorRevisar
) {
    public ResumenDashboardResponse(BigDecimal ventasHoy, Integer ventasHoyVariacionPct, long boletasHoy, long productosPorVencer,
                                    long productosPorVencerCriticos, long stockCritico, long stockAgotado, String cajaEstado,
                                    OffsetDateTime cajaHoraApertura) {
        this(ventasHoy, ventasHoyVariacionPct, boletasHoy, productosPorVencer, productosPorVencerCriticos, stockCritico,
                stockAgotado, cajaEstado, cajaHoraApertura, null);
    }
}
