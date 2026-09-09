package com.botica.backend.dao.impl;

import com.botica.backend.model.Lote;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** RowMapper sobre lotes crudo (Regla 3) — usado tras insertar, para releer la fila completa. */
public class LoteRowMapper implements RowMapper<Lote> {

    @Override
    public Lote mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Lote.builder()
                .id(rs.getLong("id"))
                .boticaId(rs.getLong("botica_id"))
                .productoId(rs.getLong("producto_id"))
                .codigo(rs.getString("codigo"))
                .fechaVencimiento(rs.getObject("fecha_vencimiento", LocalDate.class))
                .stock(rs.getInt("stock"))
                .ubicacion(rs.getString("ubicacion"))
                .costoUnitario(rs.getBigDecimal("costo_unitario"))
                .creadoEn(rs.getObject("creado_en", OffsetDateTime.class))
                .creadoPor(rs.getObject("creado_por", Long.class))
                .build();
    }
}
