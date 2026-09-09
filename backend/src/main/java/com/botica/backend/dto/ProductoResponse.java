package com.botica.backend.dto;

import com.botica.backend.model.Producto;

import java.time.LocalDate;
import java.util.List;

/**
 * estadoVencimiento/fechaVencimiento son del lote con fecha_vencimiento
 * más próxima ENTRE LOS QUE TIENEN stock > 0 (un lote agotado no tiñe
 * el producto, misma regla que Alertas) — null si el producto no tiene
 * ningún lote con stock. Igual que en Lote: el servidor manda el
 * estado (regla de negocio, umbrales configurables) y la fecha (dato);
 * el cliente arma el texto de días con diasHasta(), nunca al revés.
 */
public record ProductoResponse(
        Long id,
        String nombre,
        String laboratorio,
        String categoria,
        String codigoBarras,
        String estadoVencimiento,
        LocalDate fechaVencimiento,
        List<PresentacionResponse> presentaciones
) {
    public static ProductoResponse desde(
            Producto producto,
            String estadoVencimiento,
            LocalDate fechaVencimiento,
            List<PresentacionResponse> presentaciones
    ) {
        return new ProductoResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getLaboratorio(),
                producto.getCategoria(),
                producto.getCodigoBarras(),
                estadoVencimiento,
                fechaVencimiento,
                presentaciones
        );
    }
}
