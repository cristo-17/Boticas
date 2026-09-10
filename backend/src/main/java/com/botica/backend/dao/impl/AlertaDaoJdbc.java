package com.botica.backend.dao.impl;

import com.botica.backend.dao.AlertaDao;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Único DAO que sabe SQL de alertas. Todo WHERE incluye botica_id
 * (Regla FILTRADO POR BOTICA). Cada KPI es COUNT/SUM real contra
 * Postgres, no una lista completa filtrada en Java.
 */
@Repository
public class AlertaDaoJdbc implements AlertaDao {

    private final NamedParameterJdbcTemplate jdbc;

    public AlertaDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BigDecimal totalVentas(Long boticaId, LocalDate fecha) {
        String sql = "SELECT COALESCE(SUM(total), 0) FROM ventas WHERE botica_id = :boticaId AND fecha::date = :fecha";
        var params = new MapSqlParameterSource().addValue("boticaId", boticaId).addValue("fecha", fecha);
        return jdbc.queryForObject(sql, params, BigDecimal.class);
    }

    @Override
    public long contarVentas(Long boticaId, LocalDate fecha) {
        String sql = "SELECT COUNT(*) FROM ventas WHERE botica_id = :boticaId AND fecha::date = :fecha";
        var params = new MapSqlParameterSource().addValue("boticaId", boticaId).addValue("fecha", fecha);
        return contar(sql, params);
    }

    @Override
    public long contarProductosPorVencer(Long boticaId, LocalDate hoy, int diasMax) {
        String sql = "SELECT COUNT(DISTINCT producto_id) FROM lotes " +
                "WHERE botica_id = :boticaId AND stock > 0 " +
                "AND fecha_vencimiento > :hoy AND fecha_vencimiento <= :hoyMasDias";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("hoy", hoy)
                .addValue("hoyMasDias", hoy.plusDays(diasMax));
        return contar(sql, params);
    }

    @Override
    public long contarProductosStockCritico(Long boticaId, int umbralStockBajo) {
        String sql = "SELECT COUNT(DISTINCT producto_id) FROM lotes " +
                "WHERE botica_id = :boticaId AND stock > 0 AND stock <= :umbral";
        var params = new MapSqlParameterSource().addValue("boticaId", boticaId).addValue("umbral", umbralStockBajo);
        return contar(sql, params);
    }

    @Override
    public long contarProductosStockAgotado(Long boticaId) {
        String sql = "SELECT COUNT(DISTINCT producto_id) FROM lotes WHERE botica_id = :boticaId AND stock = 0";
        var params = new MapSqlParameterSource().addValue("boticaId", boticaId);
        return contar(sql, params);
    }

    @Override
    public List<String> nombresProductosStockBajo(Long boticaId, int umbralStockBajo, int limite) {
        String sql = "SELECT DISTINCT p.nombre FROM lotes l JOIN productos p ON p.id = l.producto_id " +
                "WHERE l.botica_id = :boticaId AND l.stock <= :umbral " +
                "ORDER BY p.nombre ASC LIMIT :limite";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("umbral", umbralStockBajo)
                .addValue("limite", limite);
        return jdbc.queryForList(sql, params, String.class);
    }

    @Override
    public List<LoteAlerta> lotesVencidosOCriticos(Long boticaId, LocalDate hoy, int criticoDias) {
        String sql = "SELECT l.id AS lote_id, p.nombre AS producto_nombre, l.codigo, l.ubicacion, l.stock, " +
                "l.fecha_vencimiento, pr.precio AS precio_unitario, " +
                "CASE WHEN l.fecha_vencimiento < :hoy THEN 'VENCIDO' ELSE 'CRITICO' END AS estado_vencimiento " +
                "FROM lotes l " +
                "JOIN productos p ON p.id = l.producto_id " +
                "LEFT JOIN presentaciones pr ON pr.producto_id = l.producto_id AND pr.factor_conversion = 1 " +
                "WHERE l.botica_id = :boticaId AND l.stock > 0 AND l.fecha_vencimiento <= :hoyMasCritico " +
                "ORDER BY l.fecha_vencimiento ASC, l.id ASC";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("hoy", hoy)
                .addValue("hoyMasCritico", hoy.plusDays(criticoDias));
        return jdbc.query(sql, params, (rs, rowNum) -> new LoteAlerta(
                rs.getLong("lote_id"),
                rs.getString("producto_nombre"),
                rs.getString("codigo"),
                rs.getString("ubicacion"),
                rs.getInt("stock"),
                rs.getObject("fecha_vencimiento", LocalDate.class),
                rs.getBigDecimal("precio_unitario"),
                rs.getString("estado_vencimiento")
        ));
    }

    @Override
    public List<CajaAbierta> cajasSinCerrar(Long boticaId, LocalDate hoy) {
        String sql = "SELECT c.id AS caja_id, c.turno, u.nombre AS usuario_nombre, c.hora_apertura " +
                "FROM caja_diaria c JOIN usuarios u ON u.id = c.usuario_id " +
                "WHERE c.botica_id = :boticaId AND c.estado = 'ABIERTA' AND c.fecha < :hoy " +
                "ORDER BY c.fecha ASC";
        var params = new MapSqlParameterSource().addValue("boticaId", boticaId).addValue("hoy", hoy);
        return jdbc.query(sql, params, (rs, rowNum) -> new CajaAbierta(
                rs.getLong("caja_id"),
                rs.getString("turno"),
                rs.getString("usuario_nombre"),
                rs.getObject("hora_apertura", java.time.OffsetDateTime.class)
        ));
    }

    private long contar(String sql, MapSqlParameterSource params) {
        Long total = jdbc.queryForObject(sql, params, Long.class);
        return total == null ? 0L : total;
    }
}
