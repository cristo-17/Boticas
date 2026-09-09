package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

public class CajaYaAbiertaException extends NegocioException {
    public CajaYaAbiertaException() {
        super(HttpStatus.CONFLICT, "CAJA_YA_ABIERTA", "Ya existe una caja abierta para hoy");
    }
}
