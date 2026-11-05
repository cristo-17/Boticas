package com.botica.backend.dto;

/**
 * Totales generales del reporte DIGEMID (RF-54). recetas cuenta cada receta una
 * vez aunque cubra varios productos; lineas es el total de filas (paginación).
 */
public record TotalesDigemidResponse(
        long lineas,
        long recetas,
        long unidades
) {
}
