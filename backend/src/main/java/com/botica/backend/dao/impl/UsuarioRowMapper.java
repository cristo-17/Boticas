package com.botica.backend.dao.impl;

import com.botica.backend.model.Usuario;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class UsuarioRowMapper implements RowMapper<Usuario> {

    @Override
    public Usuario mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Usuario.builder()
                .id(rs.getLong("id"))
                .boticaId(rs.getLong("botica_id"))
                .boticaNombre(rs.getString("botica_nombre"))
                .boticaDireccion(rs.getString("botica_direccion"))
                .nombre(rs.getString("nombre"))
                .usuario(rs.getString("usuario"))
                .passwordHash(rs.getString("password_hash"))
                .rol(rs.getString("rol"))
                .build();
    }
}
