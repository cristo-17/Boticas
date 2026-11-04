package com.botica.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Tarea 22: una revisión de una venta sin conexión (tabla revisiones_venta).
 * Viaja dentro de VentaResponse.revisiones (el cajero ve "Enviada con
 * observaciones") y en GET /api/ventas/revisiones (ADMINISTRADOR), que
 * además llena los datos de la venta. Montos crudos; el texto lo arma el cliente.
 */
public record RevisionVentaResponse(
        Long id,
        Long ventaId,
        String tipo,
        String detalle,
        BigDecimal precioCobrado,
        BigDecimal precioVigente,
        Integer unidadesFaltantes,
        String estado,
        String resolucion,
        String notaResolucion,
        OffsetDateTime creadoEn,
        OffsetDateTime resueltaEn,
        String resueltaPorNombre,
        OffsetDateTime ventaFecha,
        BigDecimal ventaTotal,
        String vendedorNombre
) {
}
