package com.botica.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record VentaResponse(
        Long id,
        OffsetDateTime fecha,
        Long usuarioId,
        List<ItemVentaResponse> items,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal total,
        String metodoPago,
        boolean sincronizada,
        UUID claveIdempotencia,
        /** Tarea 20: la receta guardada con la venta; null si ninguna línea la necesitó. */
        RecetaResponse receta,
        /** Tarea 22: true si se cobró sin conexión y llegó desde la cola del dispositivo. */
        boolean vendidaSinConexion,
        /** Tarea 22: hora del reloj del dispositivo (solo auditoría); null en línea. */
        OffsetDateTime horaDispositivo,
        /** Tarea 22: problemas encontrados al registrarla (vacío en línea). */
        List<RevisionVentaResponse> revisiones
) {
    /** Venta en línea: sin datos de la Tarea 22. */
    public VentaResponse(Long id, OffsetDateTime fecha, Long usuarioId, List<ItemVentaResponse> items, BigDecimal subtotal,
                         BigDecimal igv, BigDecimal total, String metodoPago, boolean sincronizada, UUID claveIdempotencia,
                         RecetaResponse receta) {
        this(id, fecha, usuarioId, items, subtotal, igv, total, metodoPago, sincronizada, claveIdempotencia, receta,
                false, null, List.of());
    }
}
