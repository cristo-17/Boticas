package com.botica.backend.dao.impl;

import com.botica.backend.dto.TotalDigemidProductoResponse;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/** RowMapper explícito y nombrado (Regla 3) de los totales por producto del reporte DIGEMID. */
public class TotalDigemidProductoRowMapper implements RowMapper<TotalDigemidProductoResponse> {

    @Override
    public TotalDigemidProductoResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TotalDigemidProductoResponse(
                rs.getLong("producto_id"),
                rs.getString("nombre"),
                rs.getString("unidad_nombre"),
                rs.getLong("recetas"),
                rs.getLong("unidades")
        );
    }
}
