package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

public class LoteNoEncontradoException extends NegocioException {
    public LoteNoEncontradoException() {
        super(HttpStatus.NOT_FOUND, "LOTE_NO_ENCONTRADO", "No se encontró el lote indicado.");
    }
}
