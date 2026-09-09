package com.botica.backend.dao.impl;

import com.botica.backend.model.CajaDiaria;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * RowMapper explícito y nombrado (Regla 3) — nunca una lambda anónima
 * repetida en cada consulta que toca caja_diaria.
 */
public class CajaDiariaRowMapper implements RowMapper<CajaDiaria> {

    @Override
    public CajaDiaria mapRow(ResultSet rs, int rowNum) throws SQLException {
        return CajaDiaria.builder()
                .id(rs.getLong("id"))
                .boticaId(rs.getLong("botica_id"))
                .usuarioId(rs.getLong("usuario_id"))
                .usuarioNombre(rs.getString("usuario_nombre"))
                .fecha(rs.getObject("fecha", LocalDate.class))
                .turno(rs.getString("turno"))
                .montoApertura(rs.getBigDecimal("monto_apertura"))
                .horaApertura(rs.getObject("hora_apertura", OffsetDateTime.class))
                .horaCierre(rs.getObject("hora_cierre", OffsetDateTime.class))
                .montoContado(rs.getBigDecimal("monto_contado"))
                .montoEsperado(rs.getBigDecimal("monto_esperado"))
                .diferencia(rs.getBigDecimal("diferencia"))
                .semaforoDescuadre(rs.getString("semaforo_descuadre"))
                .observaciones(rs.getString("observaciones"))
                .estado(rs.getString("estado"))
                .build();
    }
}
