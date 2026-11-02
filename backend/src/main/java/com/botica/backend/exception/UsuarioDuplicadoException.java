package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** UNIQUE global en usuarios.usuario: el login no pide elegir botica (MODELO-DATOS). */
public class UsuarioDuplicadoException extends NegocioException {
    public UsuarioDuplicadoException(String usuario) {
        super(HttpStatus.CONFLICT, "USUARIO_DUPLICADO", "El usuario " + usuario + " ya está en uso");
    }
}
