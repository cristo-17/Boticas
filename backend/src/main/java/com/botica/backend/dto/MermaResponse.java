package com.botica.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * costoUnitario NO aparece acá (D2): es información del dueño.
 * valorVenta = cantidad × precio de la presentación Unidad (factor_conversion=1).
 */
public record MermaResponse(
        Long id,
        Long loteId,
        String productoNombre,
        String loteCodigo,
        Integer cantidad,
        String motivo,
        String observacion,
        BigDecimal valorVenta,
        Long usuarioId,
        OffsetDateTime fecha
) {
}
