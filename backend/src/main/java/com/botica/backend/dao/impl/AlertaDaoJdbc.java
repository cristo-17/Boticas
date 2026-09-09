package com.botica.backend.dao.impl;

import com.botica.backend.dao.AlertaDao;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AlertaDaoJdbc implements AlertaDao {

    private final NamedParameterJdbcTemplate jdbc;

    public AlertaDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LoteAlertaRow> listarLotesVencidos(Long boticaId, LocalDate hoy) {
        String sql = """
                SELECT l.id, l.codigo, l.fecha_vencimiento, l.stock, COALESCE(pr.precio, 0) as precio_unitario, p.nombre as producto_nombre
                FROM lotes l
                JOIN productos p ON p.id = l.producto_id
                LEFT JOIN presentaciones pr ON pr.producto_id = l.producto_id AND pr.factor_conversion = 1
                WHERE l.botica_id = :boticaId AND l.stock > 0 AND l.fecha_vencimiento < :hoy
                ORDER BY l.fecha_vencimiento ASC
                LIMIT 50
                """;
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("hoy", hoy);
        return jdbc.query(sql, params, (rs, rowNum) -> new LoteAlertaRow(
                rs.getLong("id"),
                rs.getString("codigo"),
                rs.getObject("fecha_vencimiento", LocalDate.class),
                rs.getInt("stock"),
                rs.getBigDecimal("precio_unitario"),
                rs.getString("producto_nombre")
        ));
    }

    @Override
    public List<LoteAlertaRow> listarLotesPorVencer(Long boticaId, LocalDate hoy, LocalDate hoyMasCritico) {
        String sql = """
                SELECT l.id, l.codigo, l.fecha_vencimiento, l.stock, COALESCE(pr.precio, 0) as precio_unitario, p.nombre as producto_nombre
                FROM lotes l
                JOIN productos p ON p.id = l.producto_id
                LEFT JOIN presentaciones pr ON pr.producto_id = l.producto_id AND pr.factor_conversion = 1
                WHERE l.botica_id = :boticaId AND l.stock > 0 
                  AND l.fecha_vencimiento >= :hoy 
                  AND l.fecha_vencimiento <= :hoyMasCritico
                ORDER BY l.fecha_vencimiento ASC
                LIMIT 50
                """;
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("hoy", hoy)
                .addValue("hoyMasCritico", hoyMasCritico);
        return jdbc.query(sql, params, (rs, rowNum) -> new LoteAlertaRow(
                rs.getLong("id"),
                rs.getString("codigo"),
                rs.getObject("fecha_vencimiento", LocalDate.class),
                rs.getInt("stock"),
                rs.getBigDecimal("precio_unitario"),
                rs.getString("producto_nombre")
        ));
    }

    @Override
    public List<ProductoStockCriticoRow> listarProductosStockCritico(Long boticaId, int umbral) {
        String sql = """
                SELECT p.id, p.nombre, COALESCE(SUM(l.stock), 0) as stock_total
                FROM productos p
                LEFT JOIN lotes l ON l.producto_id = p.id AND l.botica_id = :boticaId
                WHERE p.botica_id = :boticaId AND p.activo = true
                GROUP BY p.id, p.nombre
                HAVING COALESCE(SUM(l.stock), 0) <= :umbral
                ORDER BY stock_total ASC
                LIMIT 50
                """;
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("umbral", umbral);
        return jdbc.query(sql, params, (rs, rowNum) -> new ProductoStockCriticoRow(
                rs.getLong("id"),
                rs.getString("nombre"),
                rs.getInt("stock_total")
        ));
    }

    @Override
    public VentasHoyRow obtenerVentasHoy(Long boticaId, LocalDate hoy) {
        String sql = """
                SELECT COALESCE(SUM(total), 0) as total_monto, COUNT(*) as cantidad_boletas
                FROM ventas
                WHERE botica_id = :boticaId AND fecha::date = :hoy
                """;
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("hoy", hoy);
        return jdbc.queryForObject(sql, params, (rs, rowNum) -> new VentasHoyRow(
                rs.getBigDecimal("total_monto"),
                rs.getInt("cantidad_boletas")
        ));
    }

    @Override
    public Optional<CajaEstadoRow> obtenerCajaHoy(Long boticaId, LocalDate hoy) {
        String sql = """
                SELECT c.estado, c.monto_apertura, c.hora_apertura, u.nombre as usuario_nombre
                FROM caja_diaria c
                JOIN usuarios u ON u.id = c.usuario_id
                WHERE c.botica_id = :boticaId AND c.fecha = :hoy
                ORDER BY c.id DESC
                LIMIT 1
                """;
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("hoy", hoy);
        return jdbc.query(sql, params, (rs, rowNum) -> new CajaEstadoRow(
                "ABIERTA".equalsIgnoreCase(rs.getString("estado")),
                rs.getString("usuario_nombre"),
                rs.getObject("hora_apertura", LocalTime.class),
                rs.getBigDecimal("monto_apertura")
        )).stream().findFirst();
    }
}
