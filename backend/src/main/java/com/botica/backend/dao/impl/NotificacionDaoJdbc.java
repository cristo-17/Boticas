package com.botica.backend.dao.impl;

import com.botica.backend.dao.NotificacionDao;
import com.botica.backend.model.Notificacion;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class NotificacionDaoJdbc implements NotificacionDao {

    private final NamedParameterJdbcTemplate jdbc;
    private final NotificacionRowMapper notificacionRowMapper = new NotificacionRowMapper();

    public NotificacionDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Notificacion insertar(Notificacion n) {
        String sql = """
                INSERT INTO notificaciones (botica_id, usuario_id, rol_destinatario, tipo, titulo, mensaje, leido, creado_por)
                VALUES (:boticaId, :usuarioId, :rolDestinatario, :tipo, :titulo, :mensaje, :leido, :creadoPor)
                """;
        var params = new MapSqlParameterSource()
                .addValue("boticaId", n.getBoticaId())
                .addValue("usuarioId", n.getUsuarioId())
                .addValue("rolDestinatario", n.getRolDestinatario())
                .addValue("tipo", n.getTipo())
                .addValue("titulo", n.getTitulo())
                .addValue("mensaje", n.getMensaje())
                .addValue("leido", n.isLeido())
                .addValue("creadoPor", n.getCreadoPor());

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(sql, params, kh, new String[]{"id", "creado_en"});
        if (kh.getKeys() != null) {
            Number id = (Number) kh.getKeys().get("id");
            if (id != null) n.setId(id.longValue());
            Object ce = kh.getKeys().get("creado_en");
            if (ce instanceof OffsetDateTime odt) {
                n.setCreadoEn(odt);
            }
        }
        return n;
    }

    @Override
    public List<Notificacion> listarRecientes(Long boticaId, String rol, int limite) {
        String sql = """
                SELECT id, botica_id, usuario_id, rol_destinatario, tipo, titulo, mensaje, leido, creado_en, creado_por
                FROM notificaciones
                WHERE botica_id = :boticaId 
                  AND (rol_destinatario IS NULL OR rol_destinatario = :rol)
                ORDER BY creado_en DESC
                LIMIT :limite
                """;
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("rol", rol)
                .addValue("limite", limite);

        return jdbc.query(sql, params, notificacionRowMapper);
    }

    @Override
    public int contarNoLeidas(Long boticaId, String rol) {
        String sql = """
                SELECT COUNT(*)
                FROM notificaciones
                WHERE botica_id = :boticaId 
                  AND (rol_destinatario IS NULL OR rol_destinatario = :rol)
                  AND leido = false
                """;
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("rol", rol);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public int marcarComoLeida(Long boticaId, String rol, Long id) {
        String sql = """
                UPDATE notificaciones
                SET leido = true
                WHERE botica_id = :boticaId
                  AND id = :id
                  AND (rol_destinatario IS NULL OR rol_destinatario = :rol)
                """;
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("rol", rol)
                .addValue("id", id);
        return jdbc.update(sql, params);
    }

    @Override
    public void marcarTodasComoLeidas(Long boticaId, String rol) {
        String sql = """
                UPDATE notificaciones 
                SET leido = true 
                WHERE botica_id = :boticaId 
                  AND (rol_destinatario IS NULL OR rol_destinatario = :rol)
                  AND leido = false
                """;
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("rol", rol);
        jdbc.update(sql, params);
    }
}
