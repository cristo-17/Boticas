package com.botica.backend.dto;

import java.time.OffsetDateTime;

/**
 * Un solo formato de error en todo el backend (Regla 7). "error" es un
 * código estable en mayúsculas: el frontend mapea contra él, nunca
 * contra "mensaje".
 */
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String mensaje,
        String path
) {
}
