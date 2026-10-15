package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Inexistente, de otra botica o no dirigida al rol de quien llama: las tres responden igual, sin revelar cuál es. */
public class NotificacionNoEncontradaException extends NegocioException {
    public NotificacionNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "NOTIFICACION_NO_ENCONTRADA", "La notificación indicada no existe");
    }
}
