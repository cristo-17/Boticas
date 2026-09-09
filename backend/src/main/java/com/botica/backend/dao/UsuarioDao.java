package com.botica.backend.dao;

import com.botica.backend.model.Usuario;

import java.util.Optional;

public interface UsuarioDao {

    Optional<Usuario> buscarPorUsuario(String usuario);

    Optional<Usuario> buscarPorId(Long id);
}
