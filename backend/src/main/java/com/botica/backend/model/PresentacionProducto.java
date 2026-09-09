package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Espeja la tabla presentaciones. La fila con factorConversion = 1
 * ("Unidad") es el precio de venta por unidad base del producto — no
 * cambia por reposición, a diferencia del costo, que es del lote (D2).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PresentacionProducto {

    private Long id;
    private Long boticaId;
    private Long productoId;
    private String etiqueta;
    private Integer factorConversion;
    private BigDecimal precio;
    private OffsetDateTime creadoEn;
}
