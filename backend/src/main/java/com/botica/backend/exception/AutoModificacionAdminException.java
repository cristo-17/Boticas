package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Tarea 17: un administrador no puede desactivarse a sí mismo ni quitarse el rol. */
public class AutoModificacionAdminException extends NegocioException {
    public AutoModificacionAdminException(String mensaje) {
        super(HttpStatus.CONFLICT, "AUTO_MODIFICACION_ADMIN", mensaje);
    }
}
