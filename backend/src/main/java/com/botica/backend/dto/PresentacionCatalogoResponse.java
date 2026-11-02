package com.botica.backend.dto;

import com.botica.backend.model.PresentacionProducto;

import java.math.BigDecimal;

/** Presentación tal como la edita el administrador: incluye las desactivadas, con su estado. */
public record PresentacionCatalogoResponse(
        Long id,
        String etiqueta,
        Integer factorConversion,
        BigDecimal precio,
        boolean activo
) {
    public static PresentacionCatalogoResponse desde(PresentacionProducto p) {
        return new PresentacionCatalogoResponse(p.getId(), p.getEtiqueta(), p.getFactorConversion(), p.getPrecio(), Boolean.TRUE.equals(p.getActivo()));
    }
}
