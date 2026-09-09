package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Usuario inexistente/inactivo o contraseña que no matchea el hash (Regla 8: nunca se distingue cuál de las dos, para no filtrar qué usuarios existen). */
public class CredencialesInvalidasException extends NegocioException {
    public CredencialesInvalidasException() {
        super(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Usuario o contraseña incorrectos");
    }
}
