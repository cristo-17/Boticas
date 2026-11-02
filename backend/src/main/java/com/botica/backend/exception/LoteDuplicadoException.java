package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Ya existe un lote con ese código para el mismo producto (UNIQUE de lotes); antes salía como 500 (DIF-09). */
public class LoteDuplicadoException extends NegocioException {
    public LoteDuplicadoException(String codigo) {
        super(HttpStatus.CONFLICT, "LOTE_DUPLICADO", "Ya existe un lote " + codigo + " para este producto");
    }
}
