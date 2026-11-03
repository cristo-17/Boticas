package com.botica.backend.dao.impl;

import com.botica.backend.model.Receta;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/** RowMapper explícito y nombrado (Regla 3) sobre recetas. */
public class RecetaRowMapper implements RowMapper<Receta> {

    @Override
    public Receta mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Receta.builder()
                .id(rs.getLong("id"))
                .boticaId(rs.getLong("botica_id"))
                .ventaId(rs.getLong("venta_id"))
                .numero(rs.getString("numero"))
                .cmp(rs.getString("cmp"))
                .medicoNombre(rs.getString("medico_nombre"))
                .fechaEmision(rs.getObject("fecha_emision", LocalDate.class))
                .creadoPor(rs.getObject("creado_por", Long.class))
                .build();
    }
}
