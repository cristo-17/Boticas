package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Tarea 22: la revisión no existe o es de otra botica (indistinguibles, D1). */
public class RevisionNoEncontradaException extends NegocioException {
    public RevisionNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "REVISION_NO_ENCONTRADA", "No se encontró la revisión indicada.");
    }
}
