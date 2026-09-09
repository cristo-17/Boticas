package com.botica.backend.event;

public record StockCriticoEvent(
        Long boticaId,
        Long productoId,
        String nombreProducto,
        int stockRestante
) {}
