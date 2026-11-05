package com.botica.backend.dto;

/** Totales al pie del reporte DIGEMID (RF-54), por producto: recetas distintas y unidades dispensadas. */
public record TotalDigemidProductoResponse(
        Long productoId,
        String producto,
        String unidadNombre,
        long recetas,
        long unidades
) {
}
