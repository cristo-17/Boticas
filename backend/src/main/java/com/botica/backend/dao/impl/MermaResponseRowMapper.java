package com.botica.backend.dao.impl;

import com.botica.backend.dto.MermaResponse;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public class MermaResponseRowMapper implements RowMapper<MermaResponse> {

    @Override
    public MermaResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new MermaResponse(
                rs.getLong("id"),
                rs.getLong("lote_id"),
                rs.getString("producto_nombre"),
                rs.getString("lote_codigo"),
                rs.getInt("cantidad"),
                rs.getString("motivo"),
                rs.getString("observacion"),
                rs.getBigDecimal("valor_venta"),
                rs.getLong("usuario_id"),
                rs.getObject("fecha", OffsetDateTime.class)
        );
    }
}
