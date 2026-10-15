package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Mismo código que JwtAccessDeniedHandler: el rol pasa el filtro, pero la regla de negocio no (p. ej. cerrar una caja ajena, D-E2-1). */
public class SinPermisoException extends NegocioException {
    public SinPermisoException() {
        super(HttpStatus.FORBIDDEN, "SIN_PERMISO", "No tienes permiso para esta acción");
    }
}
