package com.botica.backend.dto;

/** POST /api/notificaciones/ticket — ticket opaco de un solo uso para abrir el stream SSE. */
public record TicketSseResponse(String ticket, int expiraEnSegundos) {
}
