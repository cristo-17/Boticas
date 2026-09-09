package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Una fila por lote consumido (FEFO puede repartir una línea del
 * carrito entre varios lotes, así que no es 1:1 con las líneas
 * pedidas). precioUnitario y costoUnitario van CONGELADOS al momento
 * de la venta (D2) -- nunca una referencia al precio/costo actual del
 * producto o del lote. cantidad es en UNIDADES BASE (lo que de verdad
 * se descuenta de lotes.stock), no en unidades de la presentación
 * vendida.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaDetalle {

    private Long id;
    private Long ventaId;
    private Long boticaId;
    private Long productoId;
    private Long presentacionId;
    private Long loteId;
    private String nombreProducto;
    private String etiquetaPresentacion;
    private int cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal costoUnitario;
    private BigDecimal totalLinea;
    private String origenCaptura;
}
