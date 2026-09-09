package com.botica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
    @NotBlank(message = "El usuario es obligatorio") String usuario,
    @NotBlank(message = "La contraseña es obligatoria") String password,
    @NotNull(message = "El turno es obligatorio")
    @Pattern(regexp = "Mañana|Tarde|Noche", message = "El turno debe ser Mañana, Tarde o Noche")
    String turno
) {}
