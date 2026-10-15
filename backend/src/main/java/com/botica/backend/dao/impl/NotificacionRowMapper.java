package com.botica.backend.dao.impl;

import com.botica.backend.model.Notificacion;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public class NotificacionRowMapper implements RowMapper<Notificacion> {

    @Override
    public Notificacion mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Notificacion.builder()
                .id(rs.getLong("id"))
                .boticaId(rs.getLong("botica_id"))
                .usuarioId(rs.getObject("usuario_id", Long.class))
                .rolDestinatario(rs.getString("rol_destinatario"))
                .tipo(rs.getString("tipo"))
                .titulo(rs.getString("titulo"))
                .mensaje(rs.getString("mensaje"))
                .leido(rs.getBoolean("leido"))
                .creadoEn(rs.getObject("creado_en", OffsetDateTime.class))
                .creadoPor(rs.getObject("creado_por", Long.class))
                .build();
    }
}
