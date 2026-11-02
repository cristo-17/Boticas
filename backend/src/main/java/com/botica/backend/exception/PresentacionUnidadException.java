package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Regla de dominio (Tarea 17, [FE-005]): todo producto tiene exactamente una presentación con factor 1, y es siempre la misma fila (nunca se desactiva ni se reemplaza): merma, alertas y lotes la buscan con JOIN por factor_conversion = 1. */
public class PresentacionUnidadException extends NegocioException {
    public PresentacionUnidadException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, "PRESENTACION_UNIDAD_REQUERIDA", mensaje);
    }
}
