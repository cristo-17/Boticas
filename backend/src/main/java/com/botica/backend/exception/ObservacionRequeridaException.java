package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** El motivo de la merma exige observación (según /api/config) y llegó vacía. */
public class ObservacionRequeridaException extends NegocioException {
    public ObservacionRequeridaException() {
        super(HttpStatus.BAD_REQUEST, "OBSERVACION_REQUERIDA", "Este motivo requiere una observación");
    }
}
