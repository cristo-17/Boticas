package com.botica.backend.dao.impl;

import com.botica.backend.dao.ProductoDao;
import com.botica.backend.model.PresentacionProducto;
import com.botica.backend.model.Producto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Único DAO que sabe SQL de productos/presentaciones. Todo WHERE
 * incluye botica_id (Regla FILTRADO POR BOTICA, Tarea 8).
 */
@Repository
public class ProductoDaoJdbc implements ProductoDao {

    private final NamedParameterJdbcTemplate jdbc;
    private final ProductoRowMapper productoRowMapper = new ProductoRowMapper();
    private final PresentacionProductoRowMapper presentacionRowMapper = new PresentacionProductoRowMapper();

    public ProductoDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Producto> buscar(Long boticaId, String query, int limite) {
        String texto = query == null ? "" : query.trim();
        String sql = "SELECT * FROM productos WHERE botica_id = :boticaId " +
                "AND (:vacio = true OR nombre ILIKE :patron OR laboratorio ILIKE :patron OR codigo_barras ILIKE :patron) " +
                "ORDER BY nombre ASC LIMIT :limite";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("vacio", texto.isEmpty())
                .addValue("patron", "%" + texto + "%")
                .addValue("limite", limite);
        return jdbc.query(sql, params, productoRowMapper);
    }

    @Override
    public Optional<Producto> buscarPorCodigoBarras(Long boticaId, String codigoBarras) {
        String sql = "SELECT * FROM productos WHERE botica_id = :boticaId AND codigo_barras = :codigoBarras";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("codigoBarras", codigoBarras);
        return jdbc.query(sql, params, productoRowMapper).stream().findFirst();
    }

    @Override
    public List<Producto> masVendidos(Long boticaId, int limite) {
        String sql = "SELECT p.* FROM productos p " +
                "LEFT JOIN venta_detalle vd ON vd.producto_id = p.id AND vd.botica_id = :boticaId " +
                "WHERE p.botica_id = :boticaId " +
                "GROUP BY p.id " +
                "ORDER BY COALESCE(SUM(vd.cantidad), 0) DESC, p.nombre ASC " +
                "LIMIT :limite";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("limite", limite);
        return jdbc.query(sql, params, productoRowMapper);
    }

    @Override
    public Map<Long, LocalDate> fechaVencimientoMasProximaPorProductos(List<Long> productoIds) {
        if (productoIds.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT producto_id, MIN(fecha_vencimiento) AS fecha_vencimiento " +
                "FROM lotes WHERE producto_id IN (:ids) AND stock > 0 GROUP BY producto_id";
        var params = new MapSqlParameterSource().addValue("ids", productoIds);
        Map<Long, LocalDate> resultado = new HashMap<>();
        jdbc.query(sql, params, rs -> {
            resultado.put(rs.getLong("producto_id"), rs.getObject("fecha_vencimiento", LocalDate.class));
        });
        return resultado;
    }

    @Override
    public Optional<Producto> obtenerPorId(Long boticaId, Long productoId) {
        String sql = "SELECT * FROM productos WHERE botica_id = :boticaId AND id = :id";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("id", productoId);
        return jdbc.query(sql, params, productoRowMapper).stream().findFirst();
    }

    @Override
    public Optional<PresentacionProducto> obtenerPresentacion(Long boticaId, Long productoId, Long presentacionId) {
        String sql = "SELECT * FROM presentaciones WHERE botica_id = :boticaId AND producto_id = :productoId AND id = :id";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("productoId", productoId)
                .addValue("id", presentacionId);
        return jdbc.query(sql, params, presentacionRowMapper).stream().findFirst();
    }

    @Override
    public Map<Long, List<PresentacionProducto>> listarPresentacionesPorProductos(List<Long> productoIds) {
        if (productoIds.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT * FROM presentaciones WHERE producto_id IN (:ids) ORDER BY producto_id, factor_conversion DESC";
        var params = new MapSqlParameterSource().addValue("ids", productoIds);
        List<PresentacionProducto> todas = jdbc.query(sql, params, presentacionRowMapper);
        Map<Long, List<PresentacionProducto>> resultado = new HashMap<>();
        for (PresentacionProducto p : todas) {
            resultado.computeIfAbsent(p.getProductoId(), k -> new ArrayList<>()).add(p);
        }
        return resultado;
    }
}
