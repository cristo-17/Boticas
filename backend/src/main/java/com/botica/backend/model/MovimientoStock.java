package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ledger de stock (cantidad negativa = salida, positiva = ingreso).
 * origenCaptura es NULLABLE (D4): NULL en las filas que genera una
 * merma, NOT NULL de facto en las que genera una venta (heredado de
 * venta_detalle.origen_captura, que si es NOT NULL).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoStock {

    private Long id;
    private Long boticaId;
    private Long loteId;
    private String tipo;
    private int cantidad;
    private String origenCaptura;
    private Long referenciaVentaId;
    private Long referenciaMermaId;
    private Long creadoPor;
}
