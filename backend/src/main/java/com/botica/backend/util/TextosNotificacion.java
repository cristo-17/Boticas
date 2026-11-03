package com.botica.backend.util;

import java.math.BigDecimal;

/**
 * Títulos y mensajes de las notificaciones: los usan NotificacionEventListener
 * y la migración de datos demo (V12), para que una notificación generada diga
 * exactamente lo que habría dicho el servidor.
 */
public final class TextosNotificacion {

    private TextosNotificacion() {
    }

    public static String tituloDescuadreGrave(long cajaId) {
        return "Descuadre grave en Caja #" + cajaId;
    }

    public static String mensajeDescuadreGrave(String cajero, long cajaId, BigDecimal diferencia,
                                               BigDecimal esperado, BigDecimal contado) {
        return String.format(
                "El cajero %s cerró la caja #%d con una diferencia de S/ %s (Esperado: S/ %s, Contado: S/ %s).",
                cajero, cajaId, Dinero.redondear(diferencia), Dinero.redondear(esperado), Dinero.redondear(contado));
    }

    public static String tituloStockCritico(String nombreProducto) {
        return "Stock crítico: " + nombreProducto;
    }

    public static String mensajeStockCritico(String nombreProducto, int stockRestante) {
        return String.format(
                "El producto %s ha llegado a un nivel crítico: solo quedan %d unidades disponibles.",
                nombreProducto, stockRestante);
    }
}
