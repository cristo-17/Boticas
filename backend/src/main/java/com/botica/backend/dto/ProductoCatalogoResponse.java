package com.botica.backend.dto;

import com.botica.backend.model.Producto;

import java.util.List;

/**
 * Producto tal como lo ve y edita el administrador (Tarea 17): con
 * unidadNombre, su estado y TODAS sus presentaciones (activas e
 * inactivas). Sin estado de vencimiento: eso es de mostrador
 * (ProductoResponse), no de catálogo.
 */
public record ProductoCatalogoResponse(
        Long id,
        String nombre,
        String laboratorio,
        String categoria,
        String codigoBarras,
        String unidadNombre,
        boolean activo,
        List<PresentacionCatalogoResponse> presentaciones
) {
    public static ProductoCatalogoResponse desde(Producto producto, List<PresentacionCatalogoResponse> presentaciones) {
        return new ProductoCatalogoResponse(
                producto.getId(), producto.getNombre(), producto.getLaboratorio(), producto.getCategoria(),
                producto.getCodigoBarras(), producto.getUnidadNombre(), Boolean.TRUE.equals(producto.getActivo()), presentaciones);
    }
}
