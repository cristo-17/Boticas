package com.botica.backend.dto;

import java.math.BigDecimal;

/**
 * Total de egresos de caja por motivo en un rango (hoja "Egresos por motivo" del .xlsx, Tarea 18 Bloque C).
 * El motivo vive en {@code movimientos_caja.descripcion} (Bloque A).
 */
public record EgresoPorMotivoResponse(
        String motivo,
        long cantidad,
        BigDecimal total
) {}
