package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * La cantidad de la merma es mayor al stock REAL del lote, leído del
 * servidor (con FOR UPDATE) en el momento de registrar — un botón
 * deshabilitado en el cliente no es una validación (CLAUDE.md).
 */
public class CantidadExcedeStockException extends NegocioException {
    public CantidadExcedeStockException(int stockDisponible, int solicitado) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "CANTIDAD_EXCEDE_STOCK",
                "El lote solo tiene " + stockDisponible + " unidades disponibles, se pidieron " + solicitado);
    }
}
