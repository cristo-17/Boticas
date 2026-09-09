package com.botica.backend.dao.impl;

import com.botica.backend.model.MovimientoCaja;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public class MovimientoCajaRowMapper implements RowMapper<MovimientoCaja> {

    @Override
    public MovimientoCaja mapRow(ResultSet rs, int rowNum) throws SQLException {
        return MovimientoCaja.builder()
                .id(rs.getLong("id"))
                .boticaId(rs.getLong("botica_id"))
                .cajaId(rs.getLong("caja_id"))
                .tipo(rs.getString("tipo"))
                .descripcion(rs.getString("descripcion"))
                .nota(rs.getString("nota"))
                .monto(rs.getBigDecimal("monto"))
                .afectaEfectivo(rs.getBoolean("afecta_efectivo"))
                .creadoEn(rs.getObject("creado_en", OffsetDateTime.class))
                .build();
    }
}
