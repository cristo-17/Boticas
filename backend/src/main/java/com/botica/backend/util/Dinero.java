package com.botica.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Única clase que redondea dinero en todo el backend (Anexo C, regla 3).
 * Ningún setScale suelto fuera de aquí. RoundingMode.HALF_UP y escala 2,
 * siempre — comparar montos siempre con compareTo(), nunca equals().
 */
public final class Dinero {

    public static final int ESCALA = 2;

    private Dinero() {
    }

    public static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(ESCALA, RoundingMode.HALF_UP);
    }
}
