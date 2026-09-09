package com.botica.backend.config;

import java.security.Principal;

public record UsuarioPrincipal(
    Long usuarioId,
    String usuario,
    String rol,
    String turno,
    Long boticaId
) implements Principal {

    @Override
    public String getName() {
        return usuario;
    }
}
