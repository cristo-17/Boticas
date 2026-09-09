package com.botica.backend.dao.impl;

import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.model.Usuario;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class UsuarioDaoJdbc implements UsuarioDao {

    private final NamedParameterJdbcTemplate jdbc;

    public UsuarioDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String BASE_SELECT = """
            SELECT u.id, u.botica_id, u.rol_id, r.nombre AS rol_nombre,
                   u.nombre, u.usuario, u.password_hash, u.turno, u.activo,
                   b.nombre AS botica_nombre, b.direccion AS botica_direccion
            FROM usuarios u
            JOIN roles r ON u.rol_id = r.id
            JOIN boticas b ON u.botica_id = b.id
            """;

    @Override
    public Optional<Usuario> buscarPorUsuario(String usuario) {
        String sql = BASE_SELECT + " WHERE u.usuario = :usuario AND u.activo = true AND b.activo = true";
        var params = new MapSqlParameterSource("usuario", usuario);
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, this::mapRow));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        String sql = BASE_SELECT + " WHERE u.id = :id AND u.activo = true AND b.activo = true";
        var params = new MapSqlParameterSource("id", id);
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, this::mapRow));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private Usuario mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Usuario.builder()
                .id(rs.getLong("id"))
                .boticaId(rs.getLong("botica_id"))
                .rolId(rs.getLong("rol_id"))
                .rolNombre(rs.getString("rol_nombre"))
                .nombre(rs.getString("nombre"))
                .usuario(rs.getString("usuario"))
                .passwordHash(rs.getString("password_hash"))
                .turno(rs.getString("turno"))
                .activo(rs.getBoolean("activo"))
                .boticaNombre(rs.getString("botica_nombre"))
                .boticaDireccion(rs.getString("botica_direccion"))
                .build();
    }
}
