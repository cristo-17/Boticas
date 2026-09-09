package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * El parámetro ?orden= no está en la lista blanca de columnas permitidas
 * del DAO que lo recibe (Anexo D — nunca se concatena texto de cliente
 * directo en un ORDER BY).
 */
public class OrdenInvalidoException extends NegocioException {
    public OrdenInvalidoException(String campo) {
        super(HttpStatus.BAD_REQUEST, "ORDEN_INVALIDO", "No se puede ordenar por '" + campo + "'");
    }
}
