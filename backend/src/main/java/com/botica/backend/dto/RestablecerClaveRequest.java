package com.botica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/usuarios/{id}/restablecer-clave (Tarea 17, Bloque B). */
public record RestablecerClaveRequest(
        @NotBlank(message = "password es obligatorio")
        @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres")
        String password
) {
}
