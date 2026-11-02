package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Dos presentaciones activas del mismo producto con la misma etiqueta: en Punto de Venta no se distinguirían. */
public class PresentacionDuplicadaException extends NegocioException {
    public PresentacionDuplicadaException(String etiqueta) {
        super(HttpStatus.BAD_REQUEST, "PRESENTACION_DUPLICADA", "La presentación \"" + etiqueta + "\" está repetida");
    }
}
