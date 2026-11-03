package com.botica.backend.dao.impl;

import com.botica.backend.dto.FilaKardexResponse;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

/** RowMapper explícito y nombrado (Regla 3) de una fila del Kardex (KardexDaoJdbc). */
public class FilaKardexRowMapper implements RowMapper<FilaKardexResponse> {

    @Override
    public FilaKardexResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new FilaKardexResponse(
                rs.getObject("id", Long.class),
                rs.getObject("fecha", OffsetDateTime.class),
                rs.getString("tipo"),
                rs.getBoolean("entrada_inicial"),
                rs.getObject("cantidad", Integer.class),
                rs.getLong("saldo"),
                rs.getObject("lote_id", Long.class),
                rs.getString("lote_codigo"),
                rs.getObject("venta_id", Long.class),
                rs.getObject("merma_id", Long.class),
                rs.getString("receta_numero"),
                rs.getString("usuario")
        );
    }
}
