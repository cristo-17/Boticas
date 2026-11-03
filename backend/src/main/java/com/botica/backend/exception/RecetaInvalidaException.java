package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Tarea 20: datos de receta con formato válido pero imposibles (p. ej. fecha de emisión futura). */
public class RecetaInvalidaException extends NegocioException {
    public RecetaInvalidaException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, "RECETA_INVALIDA", mensaje);
    }
}
