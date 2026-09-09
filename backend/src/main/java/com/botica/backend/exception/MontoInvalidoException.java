package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

public class MontoInvalidoException extends NegocioException {
    public MontoInvalidoException() {
        super(HttpStatus.BAD_REQUEST, "MONTO_INVALIDO", "El monto debe ser mayor o igual a cero");
    }
}
