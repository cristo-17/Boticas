package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

public class CajaNoEncontradaException extends NegocioException {
    public CajaNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "CAJA_NO_ENCONTRADA", "La caja indicada no existe");
    }
}
