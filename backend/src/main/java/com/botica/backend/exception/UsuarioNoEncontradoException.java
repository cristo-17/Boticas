package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

public class UsuarioNoEncontradoException extends NegocioException {
    public UsuarioNoEncontradoException() {
        super(HttpStatus.NOT_FOUND, "USUARIO_NO_ENCONTRADO", "Usuario no encontrado");
    }
}
