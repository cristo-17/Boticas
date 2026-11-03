package com.botica.backend.dao.impl;

import com.botica.backend.dto.ResumenKardexResponse;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/** RowMapper explícito y nombrado (Regla 3) de los totales del Kardex (KardexDaoJdbc). */
public class ResumenKardexRowMapper implements RowMapper<ResumenKardexResponse> {

    @Override
    public ResumenKardexResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ResumenKardexResponse(
                rs.getLong("saldo_inicial"),
                rs.getLong("entradas"),
                rs.getLong("salidas"),
                rs.getLong("saldo_final"),
                rs.getLong("stock_actual"),
                rs.getLong("movimientos"));
    }
}
