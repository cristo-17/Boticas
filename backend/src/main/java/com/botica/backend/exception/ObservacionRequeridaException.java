package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

public class ObservacionRequeridaException extends NegocioException {
    public ObservacionRequeridaException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "OBSERVACION_REQUERIDA",
                "Este motivo de merma requiere una observación.");
    }
}
