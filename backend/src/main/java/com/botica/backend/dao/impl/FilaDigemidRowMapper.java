package com.botica.backend.dao.impl;

import com.botica.backend.dto.FilaDigemidResponse;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** RowMapper explícito y nombrado (Regla 3) de una línea del reporte DIGEMID (DigemidDaoJdbc). */
public class FilaDigemidRowMapper implements RowMapper<FilaDigemidResponse> {

    @Override
    public FilaDigemidResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new FilaDigemidResponse(
                rs.getLong("id"),
                rs.getObject("fecha", OffsetDateTime.class),
                rs.getLong("venta_id"),
                rs.getLong("producto_id"),
                rs.getString("nombre_producto"),
                rs.getString("etiqueta_presentacion"),
                rs.getInt("cantidad"),
                rs.getString("unidad_nombre"),
                rs.getObject("lote_id", Long.class),
                rs.getString("lote_codigo"),
                rs.getObject("lote_vencimiento", LocalDate.class),
                rs.getString("receta_numero"),
                rs.getString("receta_cmp"),
                rs.getString("medico_nombre"),
                rs.getObject("fecha_emision", LocalDate.class),
                rs.getString("tecnico")
        );
    }
}
