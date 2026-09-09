package com.botica.backend.dao.impl;

import com.botica.backend.model.Venta;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

public class VentaRowMapper implements RowMapper<Venta> {

    @Override
    public Venta mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Venta.builder()
                .id(rs.getLong("id"))
                .boticaId(rs.getLong("botica_id"))
                .usuarioId(rs.getLong("usuario_id"))
                .cajaId(rs.getLong("caja_id"))
                .fecha(rs.getObject("fecha", OffsetDateTime.class))
                .subtotal(rs.getBigDecimal("subtotal"))
                .igv(rs.getBigDecimal("igv"))
                .igvTasa(rs.getBigDecimal("igv_tasa"))
                .total(rs.getBigDecimal("total"))
                .metodoPago(rs.getString("metodo_pago"))
                .claveIdempotencia(UUID.fromString(rs.getString("clave_idempotencia")))
                .sincronizada(rs.getBoolean("sincronizada"))
                .build();
    }
}
