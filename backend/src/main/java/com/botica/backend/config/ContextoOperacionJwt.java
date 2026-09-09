package com.botica.backend.config;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Tarea 12: reemplaza a {@code ContextoOperacionDev} (retirada) — lee
 * usuarioId/boticaId/turno del {@link JwtPrincipal} que
 * {@link JwtAuthenticationFilter} dejó en el SecurityContext de ESTA
 * petición. @RequestScope + proxy: los Services (singleton) reciben un
 * proxy en el constructor y resuelven el valor real recién cuando se
 * llama un método, dentro de una petición real — nunca en el arranque.
 * Ningún Service cambió una línea al reemplazar la clase (ver
 * {@link ContextoOperacion}).
 * @Profile("!test"): los tests de integración que llaman Services
 * directo, sin HTTP de por medio (VentaConcurrenciaTest y similares),
 * usan ContextoOperacionTest (src/test/java) en su lugar — no hay
 * petición real ahí, así que no hay JWT que leer.
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

    private JwtPrincipal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new IllegalStateException("ContextoOperacion consultado sin autenticación JWT activa en esta petición");
        }
        return principal;
    }
}
