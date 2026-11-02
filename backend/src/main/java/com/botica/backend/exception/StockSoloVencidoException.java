package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * El producto tiene stock, pero todo vencido (DIF-13, Tarea 0B): un
 * STOCK_INSUFICIENTE "disponible 0" confunde al cajero, que ve el producto
 * en el estante. Si queda algo vendible pero no alcanza, sigue siendo
 * STOCK_INSUFICIENTE.
 */
public class StockSoloVencidoException extends NegocioException {
    public StockSoloVencidoException(String nombreProducto) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "STOCK_SOLO_VENCIDO",
                "Todo el stock de " + nombreProducto + " está vencido. Retíralo con una merma.");
    }
}
