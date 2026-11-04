package com.botica.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tarea 22: GET /api/productos/catalogo-offline. Foto del catálogo de
 * mostrador de la botica del token (solo activos, solo presentaciones
 * activas), sin paginar: es de una sola botica. generadoEn es la hora del
 * servidor. El dispositivo la guarda en IndexedDB.
 */
public record CatalogoOfflineResponse(Long boticaId, OffsetDateTime generadoEn, List<ProductoOfflineResponse> productos) {
}
