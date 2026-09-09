package com.botica.backend.exception;

/**
 * Señal interna de {@link com.botica.backend.util.JwtUtil} hacia
 * {@link com.botica.backend.config.JwtAuthenticationFilter}: token con
 * formato roto, firma que no matchea, o expirado. NUNCA llega a
 * GlobalExceptionHandler (Regla 7) — el filtro la atrapa y deja la
 * petición sin autenticar; el 401 uniforme lo arma
 * JwtAuthenticationEntryPoint cuando el endpoint protegido rechaza el
 * acceso anónimo, no esta excepción.
 */
public class TokenInvalidoException extends RuntimeException {
}
