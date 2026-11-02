package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Tarea 17: el producto está desactivado — no se vende ni recibe lotes nuevos. Sigue en Inventario, Alertas y Merma. */
public class ProductoInactivoException extends NegocioException {
    public ProductoInactivoException() {
        super(HttpStatus.CONFLICT, "PRODUCTO_INACTIVO", "El producto está desactivado");
    }

    public ProductoInactivoException(String nombreProducto) {
        super(HttpStatus.CONFLICT, "PRODUCTO_INACTIVO", nombreProducto + " está desactivado");
    }
}
