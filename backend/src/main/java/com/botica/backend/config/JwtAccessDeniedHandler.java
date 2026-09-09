package com.botica.backend.config;

import com.botica.backend.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Usuario autenticado pero sin el rol que exige el endpoint (p.ej. un
 * TECNICO contra POST /api/caja/cerrar) — 403, no 401: la identidad es
 * válida, el permiso no alcanza. Mismo formato uniforme que el resto
 * de errores (Regla 7).
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        ErrorResponse body = new ErrorResponse(OffsetDateTime.now(), HttpStatus.FORBIDDEN.value(),
                "SIN_PERMISO", "No tienes permiso para esta acción", request.getRequestURI());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
