package com.botica.backend.demo;

import java.util.Set;
import java.util.SplittableRandom;

/** Códigos EAN-13 con dígito verificador válido (prefijo GS1 Perú 775). */
public final class Ean13 {

    private Ean13() {
    }

    /** Dígito verificador de los 12 primeros dígitos: pesos 1 y 3 alternados desde la izquierda. */
    public static int digitoVerificador(String doce) {
        int suma = 0;
        for (int i = 0; i < 12; i++) {
            int digito = doce.charAt(i) - '0';
            suma += (i % 2 == 0) ? digito : digito * 3;
        }
        return (10 - suma % 10) % 10;
    }

    public static boolean esValido(String codigo) {
        return codigo != null && codigo.matches("\\d{13}")
                && digitoVerificador(codigo.substring(0, 12)) == codigo.charAt(12) - '0';
    }

    static String generar(SplittableRandom rnd, Set<String> usados) {
        while (true) {
            StringBuilder sb = new StringBuilder("775");
            for (int i = 0; i < 9; i++) {
                sb.append(rnd.nextInt(10));
            }
            String codigo = sb.toString() + digitoVerificador(sb.toString());
            if (usados.add(codigo)) {
                return codigo;
            }
        }
    }
}
