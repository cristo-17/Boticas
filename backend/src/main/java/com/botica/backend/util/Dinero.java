package com.botica.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Única clase que redondea dinero en todo el backend (Anexo C, regla 3).
 * Ningún setScale suelto fuera de aquí. RoundingMode.HALF_UP y escala 2,
 * siempre — comparar montos siempre con compareTo(), nunca equals().
 *
 * <p>Tarea D: la usa también la migración de datos demo (V12, db/seed/demo).
 * Si una regla de aquí cambia, una base reconstruida desde cero genera otra
 * historia demo (DECISIONES 2026-11-03, "V12 depende de las reglas puras").
 */
public final class Dinero {

    public static final int ESCALA = 2;

    /** Escala de las divisiones antes del redondeo final. */
    public static final int ESCALA_INTERMEDIA = 10;

    private Dinero() {
    }

    public static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(ESCALA, RoundingMode.HALF_UP);
    }

    /** Subtotal e IGV de un total ya cobrado. */
    public record DesgloseIgv(BigDecimal subtotal, BigDecimal igv) {
    }

    /**
     * Anexo C regla 7: el IGV se EXTRAE del total ya cobrado (÷ 1 + tasa),
     * nunca se suma encima. igv = total − subtotal, así que siempre cuadra.
     */
    public static DesgloseIgv extraerIgv(BigDecimal total, BigDecimal tasa) {
        BigDecimal subtotal = redondear(total.divide(BigDecimal.ONE.add(tasa), ESCALA_INTERMEDIA, RoundingMode.HALF_UP));
        return new DesgloseIgv(subtotal, total.subtract(subtotal));
    }
}
