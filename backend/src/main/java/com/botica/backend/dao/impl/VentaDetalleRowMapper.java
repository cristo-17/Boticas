package com.botica.backend.dao.impl;

import com.botica.backend.model.VentaDetalle;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class VentaDetalleRowMapper implements RowMapper<VentaDetalle> {

    @Override
    public VentaDetalle mapRow(ResultSet rs, int rowNum) throws SQLException {
        return VentaDetalle.builder()
                .id(rs.getLong("id"))
                .ventaId(rs.getLong("venta_id"))
                .boticaId(rs.getLong("botica_id"))
                .productoId(rs.getLong("producto_id"))
                .presentacionId(rs.getLong("presentacion_id"))
                .loteId(rs.getLong("lote_id"))
                .nombreProducto(rs.getString("nombre_producto"))
                .etiquetaPresentacion(rs.getString("etiqueta_presentacion"))
                .cantidad(rs.getInt("cantidad"))
                .precioUnitario(rs.getBigDecimal("precio_unitario"))
                .costoUnitario(rs.getBigDecimal("costo_unitario"))
                .totalLinea(rs.getBigDecimal("total_linea"))
                .origenCaptura(rs.getString("origen_captura"))
                .build();
    }
}
