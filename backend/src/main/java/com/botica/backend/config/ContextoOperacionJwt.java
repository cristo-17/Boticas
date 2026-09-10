package com.botica.backend.config;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Tarea 12: reemplaza a {@code ContextoOperacionDev} (retirada) — lee
 * usuarioId/boticaId/turno/rol del {@link JwtPrincipal} que
 * {@link JwtAuthenticationFilter} dejó en el SecurityContext de ESTA
 * petición.
 */
@Component
@RequestScope
@Profile("!test")
public class ContextoOperacionJwt implements ContextoOperacion {

    @Override
    public Long usuarioId() {
        return principal().usuarioId();
    }

    @Override
    public Long boticaId() {
        return principal().boticaId();
    }

    @Override
    public String turno() {
        return principal().turno();
    }

    @Override
    public String rol() {
        return principal().rol();
    }

    private JwtPrincipal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new IllegalStateException("ContextoOperacion consultado sin autenticación JWT activa en esta petición");
        }
        return principal;
    }
}
