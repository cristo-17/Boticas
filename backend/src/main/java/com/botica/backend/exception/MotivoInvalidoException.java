package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** El motivo de la merma no está entre los que devuelve /api/config — el frontend solo ofrece esos, pero el servidor no confía en eso (Regla 8). */
public class MotivoInvalidoException extends NegocioException {
    public MotivoInvalidoException(String motivo) {
        super(HttpStatus.BAD_REQUEST, "MOTIVO_INVALIDO", "Motivo no válido: '" + motivo + "'");
    }
}
