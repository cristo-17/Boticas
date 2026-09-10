package com.botica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Sin origenCaptura (docs/API-CONTRATO.md, sección Merma): el flujo de
 * merma siempre selecciona el lote de una lista, nunca escanea ni
 * digita un código. La cantidad topada al stock real y el motivo/
 * observación obligatorios se validan en el Service (Regla 8), no acá:
 * "válido contra el stock de ESE lote" y "obligatorio SI el motivo es
 * tal" no son reglas de formato.
 */
public record NuevaMermaRequest(
        @NotNull(message = "loteId es obligatorio") Long loteId,
        @NotNull(message = "cantidad es obligatoria") @Positive(message = "cantidad debe ser mayor a 0") Integer cantidad,
        @NotBlank(message = "motivo es obligatorio") String motivo,
        String observacion
) {
}
