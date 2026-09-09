package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Stock del lote no alcanza para la cantidad de merma solicitada. */
public class StockInsuficienteMermaException extends NegocioException {
    public StockInsuficienteMermaException(int disponible) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "STOCK_INSUFICIENTE_MERMA",
                "El lote solo tiene " + disponible + " unidades disponibles.");
    }
}
