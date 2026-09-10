package com.botica.backend.dao;

import com.botica.backend.model.Usuario;

import java.util.Optional;

/** No sabe qué es HTTP (Regla 2). Solo usuarios activos (activo = true) — un usuario desactivado no puede loguearse ni ser "quién soy" de un token viejo. */
public interface UsuarioDao {

    Optional<Usuario> buscarPorUsuario(String usuario);

    Optional<Usuario> buscarPorId(Long id);
}
