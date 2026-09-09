package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** La presentación no existe, no pertenece al producto indicado, o no pertenece a la botica actual. */
public class PresentacionInvalidaException extends NegocioException {
    public PresentacionInvalidaException() {
        super(HttpStatus.BAD_REQUEST, "PRESENTACION_INVALIDA", "La presentación indicada no es válida para ese producto");
    }
}
