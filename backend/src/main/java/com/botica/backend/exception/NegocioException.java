package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Base de toda excepción de regla de negocio. GlobalExceptionHandler la
 * atrapa una sola vez (Regla 7): status y código estable ya vienen
 * resueltos aquí, nunca en el handler.
 */
public abstract class NegocioException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    protected NegocioException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }
}
