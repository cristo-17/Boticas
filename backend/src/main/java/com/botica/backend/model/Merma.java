package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Espeja la tabla mermas. valorVenta (nota 3, nunca "valor" a secas) es
 * a precio de venta — el costo de la pérdida es Fase 2 (JOIN a
 * lotes.costo_unitario), no vive acá.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Merma {

    private Long id;
    private Long boticaId;
    private Long loteId;
    private Long usuarioId;
    private Integer cantidad;
    private String motivo;
    private String observacion;
    private BigDecimal valorVenta;
    private OffsetDateTime fecha;
}
