package com.botica.backend.dao.impl;

import com.botica.backend.model.RevisionVenta;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

/**
 * Lee revisiones_venta con las columnas del JOIN del listado (resuelta_por_nombre,
 * venta_fecha, venta_total, vendedor_nombre). Toda consulta de RevisionVentaDaoJdbc
 * trae esas cuatro, aunque sea en NULL, para usar un solo mapper.
 */
public class RevisionVentaRowMapper implements RowMapper<RevisionVenta> {

    @Override
    public RevisionVenta mapRow(ResultSet rs, int rowNum) throws SQLException {
        return RevisionVenta.builder()
                .id(rs.getLong("id"))
                .boticaId(rs.getLong("botica_id"))
                .ventaId(rs.getLong("venta_id"))
                .ventaDetalleId(rs.getObject("venta_detalle_id", Long.class))
                .tipo(rs.getString("tipo"))
                .detalle(rs.getString("detalle"))
                .precioCobrado(rs.getBigDecimal("precio_cobrado"))
                .precioVigente(rs.getBigDecimal("precio_vigente"))
                .unidadesFaltantes(rs.getObject("unidades_faltantes", Integer.class))
                .estado(rs.getString("estado"))
                .resolucion(rs.getString("resolucion"))
                .notaResolucion(rs.getString("nota_resolucion"))
                .resueltaPor(rs.getObject("resuelta_por", Long.class))
                .resueltaPorNombre(rs.getString("resuelta_por_nombre"))
                .resueltaEn(rs.getObject("resuelta_en", OffsetDateTime.class))
                .creadoEn(rs.getObject("creado_en", OffsetDateTime.class))
                .creadoPor(rs.getLong("creado_por"))
                .ventaFecha(rs.getObject("venta_fecha", OffsetDateTime.class))
                .ventaTotal(rs.getObject("venta_total", BigDecimal.class))
                .vendedorNombre(rs.getString("vendedor_nombre"))
                .build();
    }
}
