package com.botica.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Cálculo de una línea de venta, sin acceso a datos: lo usan
 * VentaTransaccion y la migración de datos demo (V12). Antes vivía dentro
 * de VentaTransaccion; se separó en la Tarea D sin cambiar el resultado.
 */
public final class ReglasVenta {

    private ReglasVenta() {
    }

    /**
     * cantidadBase = unidades base; totalLinea = precio × cantidad, redondeado
     * una vez (Anexo C regla 6: el TOTAL, nunca el precio unitario primero);
     * precioUnitarioBase = precio ÷ factor, solo informativo (se congela).
     */
    public record Linea(int cantidadBase, BigDecimal totalLinea, BigDecimal precioUnitarioBase) {
    }

    public static Linea calcularLinea(BigDecimal precioPresentacion, int cantidadPresentacion, int factorConversion) {
        BigDecimal totalLinea = Dinero.redondear(precioPresentacion.multiply(BigDecimal.valueOf(cantidadPresentacion)));
        BigDecimal precioUnitarioBase = Dinero.redondear(
                precioPresentacion.divide(BigDecimal.valueOf(factorConversion), Dinero.ESCALA_INTERMEDIA, RoundingMode.HALF_UP));
        return new Linea(cantidadPresentacion * factorConversion, totalLinea, precioUnitarioBase);
    }

    /**
     * Reparte el total de una línea entre los lotes que FEFO consumió, en
     * proporción a lo consumido de cada uno; la última fila se lleva el resto
     * exacto, así la suma de filas es EXACTA al total de línea.
     */
    public static List<BigDecimal> repartirTotal(BigDecimal totalLinea, List<Integer> consumidos, int cantidadBaseTotal) {
        List<BigDecimal> totales = new ArrayList<>(consumidos.size());
        BigDecimal asignado = BigDecimal.ZERO;
        int n = consumidos.size();
        for (int i = 0; i < n; i++) {
            if (i == n - 1) {
                totales.add(totalLinea.subtract(asignado));
            } else {
                BigDecimal proporcion = BigDecimal.valueOf(consumidos.get(i))
                        .divide(BigDecimal.valueOf(cantidadBaseTotal), Dinero.ESCALA_INTERMEDIA, RoundingMode.HALF_UP);
                BigDecimal fila = Dinero.redondear(totalLinea.multiply(proporcion));
                asignado = asignado.add(fila);
                totales.add(fila);
            }
        }
        return totales;
    }

    /** Una venta que deja el stock vendible del producto en el umbral o menos avisa STOCK_CRITICO. */
    public static boolean dejaStockCritico(int stockRestante, int umbralStockBajo) {
        return stockRestante <= umbralStockBajo;
    }
}
