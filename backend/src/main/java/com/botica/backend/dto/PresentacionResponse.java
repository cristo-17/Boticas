package com.botica.backend.dto;

import com.botica.backend.model.PresentacionProducto;

import java.math.BigDecimal;

/**
 * Sin campo "detalle" compuesto (hueco 5): factorConversion y
 * unidadNombre viajan por separado, el frontend arma la frase. Un
 * texto ya armado no se puede ordenar/comparar y obliga a redesplegar
 * el backend para cambiar el formato — el mismo error que
 * ventasHoyTexto (docs/DECISIONES.md).
 */
public record PresentacionResponse(
        Long id,
        String etiqueta,
        Integer factorConversion,
        String unidadNombre,
        BigDecimal precio
) {
    public static PresentacionResponse desde(PresentacionProducto presentacion, String unidadNombre) {
        return new PresentacionResponse(
                presentacion.getId(),
                presentacion.getEtiqueta(),
                presentacion.getFactorConversion(),
                unidadNombre,
                presentacion.getPrecio()
        );
    }
}
