package com.botica.backend.dao.impl;

import com.botica.backend.dto.LoteResponse;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Mapea directo a LoteResponse (no al modelo Lote): la consulta ya
 * viene con el JOIN a productos/presentaciones y el CASE WHEN de los
 * estados resuelto en SQL (mismos parámetros que el WHERE, para que
 * filtro y estado mostrado nunca puedan discrepar).
 */
public class LoteResponseRowMapper implements RowMapper<LoteResponse> {

    @Override
    public LoteResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new LoteResponse(
                rs.getLong("id"),
                rs.getLong("producto_id"),
                rs.getString("producto_nombre"),
                rs.getString("categoria"),
                rs.getString("codigo"),
                rs.getObject("fecha_vencimiento", LocalDate.class),
                rs.getInt("stock"),
                rs.getString("ubicacion"),
                rs.getBigDecimal("precio_unitario"),
                rs.getString("estado_vencimiento"),
                rs.getString("stock_estado")
        );
    }
}
