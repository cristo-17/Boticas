package com.botica.backend.dto;

import java.time.OffsetDateTime;

/**
 * Una fila del Kardex de un producto (Tarea 20, D-E2-5). Se calcula desde
 * movimientos_stock al consultar; el saldo acumulado sale de SQL
 * (SUM ... OVER), nunca de Java.
 *
 * <ul>
 *   <li>tipo: SALDO_ANTERIOR (solo con "desde", primera fila), ENTRADA,
 *       SALIDA_LIBRE, SALIDA_RECETA o MERMA.</li>
 *   <li>SALIDA_RECETA / SALIDA_LIBRE se decide por línea: es SALIDA_RECETA si
 *       el producto requiere receta y la venta tiene su receta guardada.</li>
 *   <li>entradaInicial: true en las entradas que completó la migración V10
 *       (sin usuario): "Entrada inicial (registrada por la migración)".</li>
 *   <li>cantidad: con signo (salidas negativas); null en SALDO_ANTERIOR.</li>
 *   <li>id, lote, venta, merma, receta y usuario: null cuando no aplican.</li>
 * </ul>
 */
public record FilaKardexResponse(
        Long id,
        OffsetDateTime fecha,
        String tipo,
        boolean entradaInicial,
        Integer cantidad,
        long saldo,
        Long loteId,
        String loteCodigo,
        Long ventaId,
        Long mermaId,
        String recetaNumero,
        String usuario
) {
}
