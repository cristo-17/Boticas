package com.botica.backend.config;

/**
 * Identidad extraída del JWT ya validado, guardada como principal de
 * Spring Security en el `SecurityContext` de la petición.
 * {@link ContextoOperacionJwt} lee de acá, nunca del token directo.
 */
public record JwtPrincipal(Long usuarioId, Long boticaId, String rol, String turno, String usuario) {
}
