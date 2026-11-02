package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** El factor de una presentación que ya existe no se cambia: venta_detalle.cantidad está en esa presentación, y cambiar el factor reinterpretaría las ventas pasadas. Se quita y se agrega una nueva. */
public class FactorNoEditableException extends NegocioException {
    public FactorNoEditableException(String etiqueta) {
        super(HttpStatus.BAD_REQUEST, "FACTOR_NO_EDITABLE", "El factor de \"" + etiqueta + "\" no se puede cambiar: quítala y agrega una presentación nueva");
    }
}
