package com.botica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * turno: el usuario lo elige libremente en cada login, no se valida
 * contra un horario asignado (decisión 2026-09-08, docs/DECISIONES.md)
 * — solo importa para trazabilidad, congelado en el JWT. Sí se valida
 * que sea Mañana, Tarde o Noche (DIF-12, Tarea 0B): fuera de la lista
 * llegaba al CHECK de caja_diaria al abrir caja y salía como 500.
 */
public record CredencialesLoginRequest(
        @NotBlank(message = "usuario es obligatorio") String usuario,
        @NotBlank(message = "password es obligatorio") String password,
        @NotBlank(message = "turno es obligatorio")
        @Pattern(regexp = "Mañana|Tarde|Noche", message = "El turno debe ser Mañana, Tarde o Noche") String turno
) {
}
