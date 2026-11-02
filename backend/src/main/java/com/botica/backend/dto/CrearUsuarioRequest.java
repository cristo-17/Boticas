package com.botica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/usuarios (Tarea 17, Bloque B). Sin turno (D-F-2): el turno sale del login. */
public record CrearUsuarioRequest(
        @NotBlank(message = "nombre es obligatorio")
        @Size(max = 150, message = "nombre admite hasta 150 caracteres")
        String nombre,

        @NotBlank(message = "usuario es obligatorio")
        @Size(max = 50, message = "usuario admite hasta 50 caracteres")
        String usuario,

        @NotBlank(message = "password es obligatorio")
        @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres")
        String password,

        @NotBlank(message = "rol es obligatorio")
        @Pattern(regexp = "TECNICO|ADMINISTRADOR", message = "El rol debe ser TECNICO o ADMINISTRADOR")
        String rol
) {
}
