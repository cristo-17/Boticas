package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

public class VentaNoEncontradaException extends NegocioException {
    public VentaNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "VENTA_NO_ENCONTRADA", "No se encontró la venta indicada.");
    }
}
