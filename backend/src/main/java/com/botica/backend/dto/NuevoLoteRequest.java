package com.botica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ya no manda precioUnitario (revisión de esquema 2026-09-08): el
 * precio de venta es del catálogo (presentaciones), no algo que se
 * vuelve a capturar en cada lote. costoUnitario es obligatorio desde
 * este bloque — no se puede agregar después sin perder los lotes ya
 * creados sin costo (D2).
 */
public record NuevoLoteRequest(
        @NotNull(message = "productoId es obligatorio") Long productoId,
        @NotBlank(message = "codigo es obligatorio") String codigo,
        @NotNull(message = "fechaVencimiento es obligatoria") LocalDate fechaVencimiento,
        @NotNull(message = "stock es obligatorio") Integer stock,
        String ubicacion,
        @NotNull(message = "costoUnitario es obligatorio") BigDecimal costoUnitario
) {
}
