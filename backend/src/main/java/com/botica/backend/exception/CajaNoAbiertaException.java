package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

public class CajaNoAbiertaException extends NegocioException {
    public CajaNoAbiertaException() {
        super(HttpStatus.CONFLICT, "CAJA_NO_ABIERTA", "No hay una caja abierta para esta operación");
    }
}
