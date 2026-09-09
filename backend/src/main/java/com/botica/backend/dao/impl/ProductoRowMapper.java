package com.botica.backend.dao.impl;

import com.botica.backend.model.Producto;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

/**
 * RowMapper explícito y nombrado (Regla 3) sobre productos crudo — sin
 * datos derivados (presentaciones, fecha de vencimiento más próxima),
 * esos se resuelven aparte en lote (ver ProductoDaoJdbc).
 */
public class ProductoRowMapper implements RowMapper<Producto> {

    @Override
    public Producto mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Producto.builder()
                .id(rs.getLong("id"))
                .boticaId(rs.getLong("botica_id"))
                .nombre(rs.getString("nombre"))
                .laboratorio(rs.getString("laboratorio"))
                .categoria(rs.getString("categoria"))
                .codigoBarras(rs.getString("codigo_barras"))
                .unidadNombre(rs.getString("unidad_nombre"))
                .creadoEn(rs.getObject("creado_en", OffsetDateTime.class))
                .creadoPor(rs.getObject("creado_por", Long.class))
                .build();
    }
}
