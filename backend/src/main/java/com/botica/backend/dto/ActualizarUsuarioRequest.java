package com.botica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH /api/usuarios/{id} (Tarea 17, Bloque B): nombre, rol y activo. */
public record ActualizarUsuarioRequest(
        @NotBlank(message = "nombre es obligatorio")
        @Size(max = 150, message = "nombre admite hasta 150 caracteres")
        String nombre,

        @NotBlank(message = "rol es obligatorio")
        @Pattern(regexp = "TECNICO|ADMINISTRADOR", message = "El rol debe ser TECNICO o ADMINISTRADOR")
        String rol,

        @NotNull(message = "activo es obligatorio")
        Boolean activo
) {
}
