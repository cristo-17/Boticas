package com.botica.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CerrarCajaRequest(
        @NotNull(message = "montoContado es obligatorio") BigDecimal montoContado,
        @Size(max = 500, message = "observaciones no puede superar 500 caracteres") String observaciones
) {
}
