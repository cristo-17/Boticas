package com.botica.backend.dto;

/** POST /api/auth/login (Tarea 12) — envuelve el JWT junto con el usuario, a diferencia del contrato viejo (Tarea 6) que devolvía Usuario a secas. */
public record LoginResponse(
        String token,
        UsuarioResponse usuario
) {
}
