package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Espeja la tabla ventas (cabecera). subtotal/igv/total son el
 * resultado de extraer el IGV del total ya cobrado (Anexo C, regla 7)
 * -- nunca sumado. igvTasa congela la tasa vigente al momento de la
 * venta (hueco 10): una venta vieja no se recalcula si /api/config
 * cambia mañana.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venta {

    private Long id;
    private Long boticaId;
    private Long usuarioId;
    private Long cajaId;
    private OffsetDateTime fecha;
    private BigDecimal subtotal;
    private BigDecimal igv;
    private BigDecimal igvTasa;
    private BigDecimal total;
    private String metodoPago;
    private UUID claveIdempotencia;
    private boolean sincronizada;
}
