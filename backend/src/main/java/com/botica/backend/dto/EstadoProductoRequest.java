package com.botica.backend.dto;

import jakarta.validation.constraints.NotNull;

/** PATCH /api/productos/{id}/estado. */
public record EstadoProductoRequest(@NotNull(message = "activo es obligatorio") Boolean activo) {
}
