package com.botica.backend.dto;

import java.math.BigDecimal;

/**
 * Una fila por línea PEDIDA por el cliente (no por lote consumido):
 * si FEFO repartió la línea entre varios lotes, se agregan de vuelta
 * acá para que la boleta se vea como el cajero la armó. cantidad en
 * unidades de la presentación vendida, precioUnitario es el precio de
 * esa presentación (no el derivado por unidad base). costoUnitario
 * NUNCA viaja acá (D2) -- es información del dueño.
 */
public record ItemVentaResponse(
        Long productoId,
        Long presentacionId,
        String nombre,
        String presentacion,
        BigDecimal precioUnitario,
        Integer cantidad,
        String origenCaptura
) {
}
