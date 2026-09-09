package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * 404, no 200 con cuerpo vacío (hueco 3, docs/API-CONTRATO.md). El
 * interceptor de errores del frontend no muestra toast para este
 * código — es un resultado esperado del flujo de escaneo, no una
 * falla real.
 */
public class ProductoNoEncontradoException extends NegocioException {
    public ProductoNoEncontradoException() {
        super(HttpStatus.NOT_FOUND, "PRODUCTO_NO_ENCONTRADO", "El producto indicado no existe");
    }
}
