package com.botica.backend.dto;

import com.botica.backend.model.Notificacion;

import java.time.OffsetDateTime;

/** "fechaCreacion" en el JSON es la columna creado_en (V7): el contrato con el frontend no cambió. */
public record NotificacionResponse(
        Long id,
        String tipo,
        String titulo,
        String mensaje,
        boolean leido,
        OffsetDateTime fechaCreacion
) {
    public static NotificacionResponse desde(Notificacion n) {
        return new NotificacionResponse(n.getId(), n.getTipo(), n.getTitulo(), n.getMensaje(), n.isLeido(), n.getCreadoEn());
    }
}
