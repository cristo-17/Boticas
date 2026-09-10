package com.botica.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Forma exacta de docs/API-CONTRATO.md, sección Merma. El campo es
 * valorVenta (nota 3), nunca "valor" a secas — valorCosto no es un
 * campo de Merma, vive solo en el reporte de Fase 2.
 */
public record MermaResponse(
        Long id,
        Long loteId,
        String productoNombre,
        String loteCodigo,
        Integer cantidad,
        String motivo,
        String observacion,
        BigDecimal valorVenta,
        Long usuarioId,
        OffsetDateTime fecha
) {
}
