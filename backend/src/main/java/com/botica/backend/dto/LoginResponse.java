package com.botica.backend.dto;

public record LoginResponse(
    String token,
    UsuarioResponse usuario
) {}
