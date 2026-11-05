package com.botica.backend.dao.impl;

import com.botica.backend.dto.TotalesDigemidResponse;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/** RowMapper explícito y nombrado (Regla 3) de los totales generales del reporte DIGEMID. */
public class TotalesDigemidRowMapper implements RowMapper<TotalesDigemidResponse> {

    @Override
    public TotalesDigemidResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TotalesDigemidResponse(rs.getLong("lineas"), rs.getLong("recetas"), rs.getLong("unidades"));
    }
}
