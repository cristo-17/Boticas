package com.botica.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record NuevaMermaRequest(
        @NotNull(message = "loteId es obligatorio") Long loteId,
        @NotNull(message = "cantidad es obligatoria") @Min(value = 1, message = "cantidad debe ser >= 1") Integer cantidad,
        @NotBlank(message = "motivo es obligatorio")
        @Pattern(regexp = "Vencimiento|Rotura|Deterioro|Robo o pérdida|Otro",
                 message = "motivo no válido")
        String motivo,
        String observacion
) {
}
