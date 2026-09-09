package com.botica.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * turno: el usuario lo elige libremente en cada login, no se valida
 * contra un horario asignado (decisión 2026-09-08, docs/DECISIONES.md)
 * — solo importa para la regla de caja (una abierta por usuario/turno/
 * día) y para trazabilidad, congelado en el JWT.
 */
public record CredencialesLoginRequest(
        @NotBlank(message = "usuario es obligatorio") String usuario,
        @NotBlank(message = "password es obligatorio") String password,
        @NotBlank(message = "turno es obligatorio") String turno
) {
}
