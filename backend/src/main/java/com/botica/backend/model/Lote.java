package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Espeja la tabla lotes. costoUnitario existe acá (no en el precio de
 * venta, que es de presentaciones) y nunca se expone en GET /api/lotes
 * — es información del dueño, no del cajero (D2, docs/API-CONTRATO.md).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lote {

    private Long id;
    private Long boticaId;
    private Long productoId;
    private String codigo;
    private LocalDate fechaVencimiento;
    private Integer stock;
    private String ubicacion;
    private BigDecimal costoUnitario;
    private OffsetDateTime creadoEn;
    private Long creadoPor;
}
