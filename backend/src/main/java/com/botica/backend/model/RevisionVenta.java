package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Espeja revisiones_venta (V13, Tarea 22): un problema de una venta sin conexión, que nunca se rechaza. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevisionVenta {

    public static final String STOCK_INSUFICIENTE = "STOCK_INSUFICIENTE";
    public static final String PRECIO_DISTINTO = "PRECIO_DISTINTO";
    public static final String CAJA_CERRADA = "CAJA_CERRADA";
    public static final String OTRO = "OTRO";

    public static final String PENDIENTE = "PENDIENTE";
    public static final String RESUELTA = "RESUELTA";

    public static final String VISTA = "VISTA";
    public static final String FALTANTE = "FALTANTE";

    private Long id;
    private Long boticaId;
    private Long ventaId;
    private Long ventaDetalleId;
    private String tipo;
    private String detalle;
    private BigDecimal precioCobrado;
    private BigDecimal precioVigente;
    private Integer unidadesFaltantes;
    private String estado;
    private String resolucion;
    private String notaResolucion;
    private Long resueltaPor;
    private String resueltaPorNombre;
    private OffsetDateTime resueltaEn;
    private OffsetDateTime creadoEn;
    private Long creadoPor;
    /** Solo en el listado del administrador (JOIN con ventas y usuarios). */
    private OffsetDateTime ventaFecha;
    private BigDecimal ventaTotal;
    private String vendedorNombre;
}
