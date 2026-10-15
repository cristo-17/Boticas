package com.botica.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CerrarCajaRequest(
        @NotNull(message = "montoContado es obligatorio") BigDecimal montoContado,
        @Size(max = 500, message = "observaciones no puede superar 500 caracteres") String observaciones,
        // D-E2-1: null = la caja abierta de quien llama. Con id, solo ADMINISTRADOR puede cerrar una ajena.
        Long cajaId
) {
}
