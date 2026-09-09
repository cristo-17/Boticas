package com.botica.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

/**
 * claveIdempotencia: UUID v4 generado por el FRONTEND al confirmar el
 * carrito, no en cada intento HTTP -- se reusa en cada reintento de
 * red del mismo POST. Si ya existe una venta con esa clave, el
 * servidor devuelve esa venta con 200 en vez de crear otra.
 */
public record NuevaVentaRequest(
        @NotNull(message = "claveIdempotencia es obligatoria") UUID claveIdempotencia,
        @NotEmpty(message = "items no puede estar vacío") @Valid List<ItemVentaRequest> items,
        @Pattern(regexp = "efectivo|yape|tarjeta", message = "metodoPago debe ser efectivo, yape o tarjeta") String metodoPago
) {
}
