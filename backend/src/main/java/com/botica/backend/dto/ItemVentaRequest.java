package com.botica.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * cantidad es en unidades de la PRESENTACIÓN vendida (p.ej. "2" cajas),
 * no en unidades base -- el servidor la convierte (Regla de Bloque B,
 * paso 3). El cliente nunca manda montos (Regla 8): solo ids, cantidad
 * y de dónde salió la línea.
 */
public record ItemVentaRequest(
        @NotNull(message = "productoId es obligatorio") Long productoId,
        @NotNull(message = "presentacionId es obligatorio") Long presentacionId,
        @NotNull(message = "cantidad es obligatoria") @Positive(message = "cantidad debe ser mayor a cero") Integer cantidad,
        @Pattern(regexp = "ESCANEO|MANUAL|BUSQUEDA", message = "origenCaptura debe ser ESCANEO, MANUAL o BUSQUEDA") String origenCaptura
) {
}
