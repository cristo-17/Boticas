package com.botica.backend.dto;

import java.time.OffsetDateTime;

public record NotificacionResponse(
        Long id,
        String tipo,
        String titulo,
        String mensaje,
        boolean leido,
        OffsetDateTime fechaCreacion
) {}
