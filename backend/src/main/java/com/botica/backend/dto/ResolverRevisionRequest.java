package com.botica.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Tarea 22: VISTA (cualquier tipo) o FALTANTE (solo STOCK_INSUFICIENTE, con
 * nota obligatoria: las unidades se dan por faltante de inventario y la línea
 * se queda sin lote ni costo). APLICAR_LOTE quedó fuera de esta entrega.
 */
public record ResolverRevisionRequest(
        @NotNull(message = "accion es obligatoria")
        @Pattern(regexp = "VISTA|FALTANTE", message = "accion debe ser VISTA o FALTANTE") String accion,
        @Size(max = 300, message = "nota admite hasta 300 caracteres") String nota
) {
}
