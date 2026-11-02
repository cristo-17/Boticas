package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

public class RangoFechasInvalidoException extends NegocioException {
    public RangoFechasInvalidoException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, "RANGO_FECHAS_INVALIDO", mensaje);
    }
}
