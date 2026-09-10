package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** El lote no existe en esta botica, o no pertenece al producto indicado. */
public class LoteNoEncontradoException extends NegocioException {
    public LoteNoEncontradoException() {
        super(HttpStatus.NOT_FOUND, "LOTE_NO_ENCONTRADO", "El lote indicado no existe");
    }
}
