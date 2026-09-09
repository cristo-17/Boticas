package com.botica.backend.dto;

public record UsuarioResponse(
    Long id,
    String nombre,
    String usuario,
    String rol,
    String turno,
    String sede
) {}
