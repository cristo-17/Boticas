package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** El stock disponible en todos los lotes del producto no alcanza para la cantidad pedida — rollback completo de la venta. */
public class StockInsuficienteException extends NegocioException {
    public StockInsuficienteException(String nombreProducto, int disponible, int solicitado) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "STOCK_INSUFICIENTE",
                "Stock insuficiente de " + nombreProducto + ": disponible " + disponible + ", solicitado " + solicitado);
    }
}
