package com.botica.backend.dao.impl;

import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.model.Usuario;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UsuarioDaoJdbc implements UsuarioDao {

    private static final String SELECT_BASE =
            "SELECT u.id, u.botica_id, b.nombre AS botica_nombre, b.direccion AS botica_direccion, " +
                    "u.nombre, u.usuario, u.password_hash, r.nombre AS rol " +
                    "FROM usuarios u " +
                    "JOIN roles r ON r.id = u.rol_id " +
                    "JOIN boticas b ON b.id = u.botica_id " +
                    "WHERE u.activo = true AND ";

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioRowMapper rowMapper = new UsuarioRowMapper();

    public UsuarioDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Usuario> buscarPorUsuario(String usuario) {
        String sql = SELECT_BASE + "u.usuario = :usuario";
        var params = new MapSqlParameterSource().addValue("usuario", usuario);
        return jdbc.query(sql, params, rowMapper).stream().findFirst();
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        String sql = SELECT_BASE + "u.id = :id";
        var params = new MapSqlParameterSource().addValue("id", id);
        return jdbc.query(sql, params, rowMapper).stream().findFirst();
    }
}
