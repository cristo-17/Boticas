package com.botica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Bean Validation solo verifica presencia/formato (Regla 8, nivel 1) —
 * que el monto no sea negativo es regla de negocio y se valida en
 * CajaService, para poder devolver el código estable MONTO_INVALIDO en
 * vez del genérico FORMATO_INVALIDO de una anotación de validación.
 */
public record AbrirCajaRequest(
        @NotNull(message = "montoInicial es obligatorio") BigDecimal montoInicial,
        @NotBlank(message = "turno es obligatorio") String turno
) {
}
