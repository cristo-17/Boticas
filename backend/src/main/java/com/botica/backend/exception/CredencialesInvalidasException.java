package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Lanzada cuando el usuario no existe o la contraseña no coincide.
 * Devuelve 401 CREDENCIALES_INVALIDAS (API-CONTRATO.md).
 */
public class CredencialesInvalidasException extends NegocioException {

    public CredencialesInvalidasException() {
        super(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Usuario o contraseña incorrectos.");
    }
}
