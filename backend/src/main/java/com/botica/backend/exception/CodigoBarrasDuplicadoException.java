package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** UNIQUE (botica_id, codigo_barras) de productos: el código ya es de otro producto de esta botica. */
public class CodigoBarrasDuplicadoException extends NegocioException {
    public CodigoBarrasDuplicadoException(String codigoBarras) {
        super(HttpStatus.CONFLICT, "CODIGO_BARRAS_DUPLICADO", "El código de barras " + codigoBarras + " ya es de otro producto");
    }
}
