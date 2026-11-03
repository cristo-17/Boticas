package com.botica.backend.util;

import java.math.BigDecimal;

/**
 * Cálculo del cierre de caja, sin acceso a datos: lo usan CajaService y la
 * migración de datos demo (V12). Se separó de CajaService en la Tarea D sin
 * cambiar el resultado.
 */
public final class ReglasCaja {

    private ReglasCaja() {
    }

    public record Cierre(BigDecimal esperado, BigDecimal contado, BigDecimal diferencia, String semaforo) {
    }

    /** esperado = apertura + movimientos que afectan efectivo; diferencia = contado − esperado. */
    public static Cierre calcularCierre(BigDecimal montoApertura, BigDecimal sumaMovimientosEfectivo,
                                        BigDecimal montoContado, BigDecimal umbralLeve) {
        BigDecimal esperado = Dinero.redondear(montoApertura.add(sumaMovimientosEfectivo));
        BigDecimal contado = Dinero.redondear(montoContado);
        BigDecimal diferencia = Dinero.redondear(contado.subtract(esperado));
        return new Cierre(esperado, contado, diferencia, semaforo(diferencia, umbralLeve));
    }

    /** EXACTO si no hay diferencia; LEVE hasta el umbral (inclusive); GRAVE por encima. */
    public static String semaforo(BigDecimal diferencia, BigDecimal umbralLeve) {
        BigDecimal absoluta = diferencia.abs();
        if (absoluta.compareTo(BigDecimal.ZERO) == 0) {
            return "EXACTO";
        }
        if (absoluta.compareTo(umbralLeve) <= 0) {
            return "LEVE";
        }
        return "GRAVE";
    }
}
