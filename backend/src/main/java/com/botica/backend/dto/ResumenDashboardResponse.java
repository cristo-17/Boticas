package com.botica.backend.dto;

public record ResumenDashboardResponse(
        String ventasHoyTexto,
        String ventasHoyNota,
        int productosPorVencer,
        String productosPorVencerNota,
        int stockCritico,
        String stockCriticoNota,
        String cajaEstado,
        String cajaNota
) {}
