package com.botica.backend.config;

import com.botica.backend.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Un endpoint protegido sin token (o con uno inválido/expirado) llega
 * hasta acá -- ANTES del DispatcherServlet, así que GlobalExceptionHandler
 * (Regla 7) nunca lo ve. Arma el MISMO formato de error a mano, para que
 * el frontend siga leyendo un solo shape sin importar en qué capa nació
 * el 401.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ErrorResponse body = new ErrorResponse(OffsetDateTime.now(), HttpStatus.UNAUTHORIZED.value(),
                "NO_AUTENTICADO", "Token ausente, inválido o expirado", request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
