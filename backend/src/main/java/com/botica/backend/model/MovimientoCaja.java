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
public class MovimientoCaja {

    private Long id;
    private Long boticaId;
    private Long cajaId;
    private String tipo;
    private String descripcion;
    private String nota;
    private BigDecimal monto;
    private boolean afectaEfectivo;
    private OffsetDateTime creadoEn;
}
