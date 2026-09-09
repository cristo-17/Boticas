package com.botica.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * costoUnitario NO aparece acá a propósito (D2, docs/API-CONTRATO.md):
 * es información del dueño, no del cajero. precioUnitario sale de
 * presentaciones (la fila "Unidad", factorConversion = 1), no de una
 * columna de lotes — el precio de venta no cambia por reposición.
 */
public record LoteResponse(
        Long id,
        Long productoId,
        String productoNombre,
        String categoria,
        String codigo,
        LocalDate fechaVencimiento,
        Integer stock,
        String ubicacion,
        BigDecimal precioUnitario,
        String estadoVencimiento,
        String stockEstado
) {
}
