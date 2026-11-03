package com.botica.backend.dto;

/**
 * Totales del Kardex en el rango, calculados en SQL (R19).
 * saldoFinal = saldoInicial + entradas − salidas. stockActual es la suma
 * del stock de los lotes del producto hoy: sin "hasta" (o con "hasta" en
 * hoy o después) tiene que ser igual a saldoFinal — es el invariante.
 */
public record ResumenKardexResponse(
        long saldoInicial,
        long entradas,
        long salidas,
        long saldoFinal,
        long stockActual,
        long movimientos
) {
}
