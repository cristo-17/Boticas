package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** No se puede vender sin una caja abierta del usuario actual. */
public class SinCajaAbiertaException extends NegocioException {
    public SinCajaAbiertaException() {
        super(HttpStatus.CONFLICT, "SIN_CAJA_ABIERTA", "No hay una caja abierta para registrar la venta");
    }
}
