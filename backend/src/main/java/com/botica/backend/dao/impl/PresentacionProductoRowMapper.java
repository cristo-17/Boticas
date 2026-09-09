package com.botica.backend.dao.impl;

import com.botica.backend.model.PresentacionProducto;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public class PresentacionProductoRowMapper implements RowMapper<PresentacionProducto> {

    @Override
    public PresentacionProducto mapRow(ResultSet rs, int rowNum) throws SQLException {
        return PresentacionProducto.builder()
                .id(rs.getLong("id"))
                .boticaId(rs.getLong("botica_id"))
                .productoId(rs.getLong("producto_id"))
                .etiqueta(rs.getString("etiqueta"))
                .factorConversion(rs.getInt("factor_conversion"))
                .precio(rs.getBigDecimal("precio"))
                .creadoEn(rs.getObject("creado_en", OffsetDateTime.class))
                .build();
    }
}
