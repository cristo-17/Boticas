package com.botica.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * POST /api/caja/egresos (Tarea 18, Bloque A).
 * - El monto va en positivo en la petición y se guarda en negativo en movimientos_caja.
 * - No se valida contra el efectivo disponible en caja (rompería el conteo ciego).
 * - En movimientos_caja: motivo -> columna descripcion; descripcion -> columna nota.
 * - cajaId opcional: sin cajaId usa la caja abierta del usuario; con cajaId, la caja indicada
 *   (permitido para su dueño o un ADMINISTRADOR).
 */
public record RegistrarEgresoRequest(
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        @Digits(integer = 8, fraction = 2, message = "El monto admite hasta 2 decimales")
        BigDecimal monto,

        @NotBlank(message = "El motivo es obligatorio")
        String motivo,

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 250, message = "La descripción no puede superar los 250 caracteres")
        String descripcion,

        Long cajaId
) {
}
