package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Tarea 22: FALTANTE solo para STOCK_INSUFICIENTE, y siempre con nota. */
public class ResolucionInvalidaException extends NegocioException {
    public ResolucionInvalidaException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, "RESOLUCION_INVALIDA", mensaje);
    }
}
