package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Tarea 20: la venta tiene al menos una línea de un producto con requiereReceta y no trae los datos de la receta. */
public class RecetaRequeridaException extends NegocioException {
    public RecetaRequeridaException(String productos) {
        super(HttpStatus.BAD_REQUEST, "RECETA_REQUERIDA",
                "La venta necesita los datos de la receta (número y CMP del médico): " + productos);
    }
}
