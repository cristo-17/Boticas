package com.botica.backend.dao.impl;

import com.botica.backend.dao.MermaDao;
import com.botica.backend.dto.MermaResponse;
import com.botica.backend.model.Merma;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Repository
public class MermaDaoJdbc implements MermaDao {

    private final NamedParameterJdbcTemplate jdbc;

    public MermaDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Merma insertar(Merma merma) {
        String sql = """
                INSERT INTO mermas (botica_id, lote_id, usuario_id, cantidad, motivo, observacion, valor_venta)
                VALUES (:boticaId, :loteId, :usuarioId, :cantidad, :motivo, :observacion, :valorVenta)
                """;
        var params = new MapSqlParameterSource()
                .addValue("boticaId", merma.getBoticaId())
                .addValue("loteId", merma.getLoteId())
                .addValue("usuarioId", merma.getUsuarioId())
                .addValue("cantidad", merma.getCantidad())
                .addValue("motivo", merma.getMotivo())
                .addValue("observacion", merma.getObservacion())
                .addValue("valorVenta", merma.getValorVenta());
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(sql, params, kh, new String[]{"id", "fecha"});
        merma.setId(kh.getKeys() != null ? ((Number) kh.getKeys().get("id")).longValue() : null);
        var ts = kh.getKeys() != null ? kh.getKeys().get("fecha") : null;
        if (ts instanceof java.time.OffsetDateTime odt) {
            merma.setFecha(odt);
        }
        return merma;
    }

    @Override
    public List<MermaResponse> listarDelDia(Long boticaId, LocalDate hoy) {
        String sql = """
                SELECT m.id, m.lote_id, p.nombre AS producto_nombre, l.codigo AS lote_codigo,
                       m.cantidad, m.motivo, m.observacion, m.valor_venta, m.usuario_id, m.fecha
                FROM mermas m
                JOIN lotes l ON l.id = m.lote_id
                JOIN productos p ON p.id = l.producto_id
                WHERE m.botica_id = :boticaId
                  AND m.fecha::date = :hoy
                ORDER BY m.fecha DESC
                """;
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("hoy", hoy);
        return jdbc.query(sql, params, MermaDaoJdbc::mapRow);
    }

    @Override
    public void insertarMovimientoStock(Long boticaId, Long loteId, int cantidad, Long mermaId, Long creadoPor) {
        String sql = """
                INSERT INTO movimientos_stock (botica_id, lote_id, tipo, cantidad, referencia_merma_id, creado_por)
                VALUES (:boticaId, :loteId, 'MERMA', :cantidad, :mermaId, :creadoPor)
                """;
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("loteId", loteId)
                .addValue("cantidad", -cantidad)   // salida → negativo
                .addValue("mermaId", mermaId)
                .addValue("creadoPor", creadoPor);
        jdbc.update(sql, params);
    }

    private static MermaResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        var ts = rs.getObject("fecha", java.time.OffsetDateTime.class);
        return new MermaResponse(
                rs.getLong("id"),
                rs.getLong("lote_id"),
                rs.getString("producto_nombre"),
                rs.getString("lote_codigo"),
                rs.getInt("cantidad"),
                rs.getString("motivo"),
                rs.getString("observacion"),
                rs.getBigDecimal("valor_venta"),
                rs.getLong("usuario_id"),
                ts
        );
    }
}
