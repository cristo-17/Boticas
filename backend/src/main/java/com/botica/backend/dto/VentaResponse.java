package com.botica.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record VentaResponse(
        Long id,
        OffsetDateTime fecha,
        Long usuarioId,
        List<ItemVentaResponse> items,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal total,
        String metodoPago,
        boolean sincronizada,
        UUID claveIdempotencia
) {
}
