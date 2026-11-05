package com.botica.backend.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Una línea dispensada con receta (RF-54, GET /api/reportes/digemid): una fila
 * de venta_detalle con requiere_receta congelado en true. Se calcula al consultar.
 *
 * <ul>
 *   <li>cantidad: unidades base del producto (unidadNombre), no de la presentación.</li>
 *   <li>loteId, loteCodigo y loteVencimiento: null en una línea sin lote (venta
 *       sin conexión con stock insuficiente, en revisión).</li>
 *   <li>recetaNumero y recetaCmp: null solo si la venta sin conexión llegó sin
 *       receta (queda una revisión). medicoNombre y fechaEmision son opcionales.</li>
 *   <li>tecnico: nombre del usuario que registró la venta.</li>
 * </ul>
 */
public record FilaDigemidResponse(
        Long id,
        OffsetDateTime fecha,
        Long ventaId,
        Long productoId,
        String producto,
        String presentacion,
        int cantidad,
        String unidadNombre,
        Long loteId,
        String loteCodigo,
        LocalDate loteVencimiento,
        String recetaNumero,
        String recetaCmp,
        String medicoNombre,
        LocalDate fechaEmision,
        String tecnico
) {
}
