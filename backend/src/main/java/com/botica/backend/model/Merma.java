package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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
    /** Precio de venta al momento del registro (presentacion factor_conversion=1 × cantidad). */
    private BigDecimal valorVenta;
    private OffsetDateTime fecha;
}
