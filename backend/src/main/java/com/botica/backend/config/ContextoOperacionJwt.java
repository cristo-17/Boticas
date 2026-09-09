package com.botica.backend.config;

import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link ContextoOperacion} que extrae la identidad del
 * usuario, botica y turno directamente del JWT validado en la petición actual.
 *
 * Reemplaza a {@link ContextoOperacionDev} como bean primario.
 */
@Component
@Primary
public class ContextoOperacionJwt implements ContextoOperacion {

    private static final Long DEFAULT_USUARIO_ID = 1L;
    private static final Long DEFAULT_BOTICA_ID = 1L;
    private static final String DEFAULT_TURNO = "Tarde";

    private UsuarioPrincipal obtenerPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal up) {
            return up;
        }
        return null;
    }

    @Override
    public Long usuarioId() {
        UsuarioPrincipal up = obtenerPrincipal();
        return up != null && up.usuarioId() != null ? up.usuarioId() : DEFAULT_USUARIO_ID;
    }

    @Override
    public Long boticaId() {
        UsuarioPrincipal up = obtenerPrincipal();
        return up != null && up.boticaId() != null ? up.boticaId() : DEFAULT_BOTICA_ID;
    }

    @Override
    public String turno() {
        UsuarioPrincipal up = obtenerPrincipal();
        return up != null && up.turno() != null ? up.turno() : DEFAULT_TURNO;
    }

    @Override
    public String rol() {
        UsuarioPrincipal up = obtenerPrincipal();
        return up != null && up.rol() != null ? up.rol() : "ADMINISTRADOR";
    }
}
