package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Un parámetro de filtro (p.ej. ?vencimiento=) no está entre los valores aceptados. */
public class FiltroInvalidoException extends NegocioException {
    public FiltroInvalidoException(String parametro, String valor) {
        super(HttpStatus.BAD_REQUEST, "FILTRO_INVALIDO", "Valor no válido para " + parametro + ": '" + valor + "'");
    }
}
