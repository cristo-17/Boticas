package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Tarea 22: una venta con sinConexion trae precioCobrado en cada línea; sin él, el cuerpo está mal formado. */
public class PrecioCobradoRequeridoException extends NegocioException {
    public PrecioCobradoRequeridoException() {
        super(HttpStatus.BAD_REQUEST, "FORMATO_INVALIDO", "items.precioCobrado es obligatorio en una venta sin conexión");
    }
}
