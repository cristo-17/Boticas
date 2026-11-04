package com.botica.backend.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * cantidad es en unidades de la PRESENTACIÓN vendida (p.ej. "2" cajas),
 * no en unidades base -- el servidor la convierte (Regla de Bloque B,
 * paso 3). El cliente nunca manda montos (Regla 8): solo ids, cantidad
 * y de dónde salió la línea.
 *
 * Única excepción, Tarea 22 (D-E2-7): precioCobrado, el precio de la
 * presentación que el cajero vio y cobró sin conexión. Obligatorio con
 * NuevaVentaRequest.sinConexion e ignorado sin él (en línea manda el precio
 * actual). Si difiere del vigente, queda una revisión PRECIO_DISTINTO.
 */
public record ItemVentaRequest(
        @NotNull(message = "productoId es obligatorio") Long productoId,
        @NotNull(message = "presentacionId es obligatorio") Long presentacionId,
        @NotNull(message = "cantidad es obligatoria") @Positive(message = "cantidad debe ser mayor a cero") Integer cantidad,
        @Pattern(regexp = "ESCANEO|MANUAL|BUSQUEDA", message = "origenCaptura debe ser ESCANEO, MANUAL o BUSQUEDA") String origenCaptura,
        @Positive(message = "precioCobrado debe ser mayor a cero")
        @Digits(integer = 8, fraction = 2, message = "precioCobrado admite hasta 2 decimales") BigDecimal precioCobrado
) {
    /** Línea de una venta en línea (sin precioCobrado). */
    public ItemVentaRequest(Long productoId, Long presentacionId, Integer cantidad, String origenCaptura) {
        this(productoId, presentacionId, cantidad, origenCaptura, null);
    }
}
