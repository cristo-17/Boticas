package com.botica.backend.dao.impl;

import com.botica.backend.dao.ProductoDao;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.exception.OrdenInvalidoException;
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

    /** Lista blanca de columnas ordenables del catálogo (Anexo D). */
    private static final Map<String, String> COLUMNAS_ORDEN_CATALOGO = Map.of(
            "nombre", "nombre",
            "categoria", "categoria",
            "laboratorio", "laboratorio"
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final ProductoRowMapper productoRowMapper = new ProductoRowMapper();
    private final PresentacionProductoRowMapper presentacionRowMapper = new PresentacionProductoRowMapper();

    public ProductoDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Producto> buscar(Long boticaId, String query, boolean incluirInactivos, int limite) {
        String texto = query == null ? "" : query.trim();
        String sql = "SELECT * FROM productos WHERE botica_id = :boticaId " +
                "AND (:incluirInactivos = true OR activo = true) " +
                "AND (:vacio = true OR nombre ILIKE :patron OR laboratorio ILIKE :patron OR codigo_barras ILIKE :patron) " +
                "ORDER BY nombre ASC LIMIT :limite";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("incluirInactivos", incluirInactivos)
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
    public List<Producto> masVendidos(Long boticaId, int limite, LocalDate desde) {
        // fecha::date es el día de Lima: la conexión fija la zona (Regla 5), igual que AlertaDaoJdbc.
        String sql = "SELECT p.* FROM productos p " +
                "LEFT JOIN (venta_detalle vd JOIN ventas v ON v.id = vd.venta_id AND v.fecha::date >= :desde) " +
                "ON vd.producto_id = p.id AND vd.botica_id = :boticaId " +
                "WHERE p.botica_id = :boticaId AND p.activo = true " +
                "GROUP BY p.id " +
                "ORDER BY COALESCE(SUM(vd.cantidad), 0) DESC, p.nombre ASC " +
                "LIMIT :limite";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("limite", limite)
                .addValue("desde", desde);
        return jdbc.query(sql, params, productoRowMapper);
    }

    @Override
    public Map<Long, LocalDate> fechaVencimientoMasProximaPorProductos(List<Long> productoIds, LocalDate hoy) {
        if (productoIds.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT producto_id, MIN(fecha_vencimiento) AS fecha_vencimiento " +
                "FROM lotes WHERE producto_id IN (:ids) AND stock > 0 AND fecha_vencimiento >= :hoy GROUP BY producto_id";
        var params = new MapSqlParameterSource().addValue("ids", productoIds).addValue("hoy", hoy);
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
        String sql = "SELECT * FROM presentaciones WHERE producto_id IN (:ids) ORDER BY producto_id, factor_conversion DESC, id ASC";
        var params = new MapSqlParameterSource().addValue("ids", productoIds);
        List<PresentacionProducto> todas = jdbc.query(sql, params, presentacionRowMapper);
        Map<Long, List<PresentacionProducto>> resultado = new HashMap<>();
        for (PresentacionProducto p : todas) {
            resultado.computeIfAbsent(p.getProductoId(), k -> new ArrayList<>()).add(p);
        }
        return resultado;
    }
    @Override
    public PaginaResponse<Producto> listarCatalogo(Long boticaId, String buscar, int pagina, int tamano, String orden) {
        int paginaSegura = Math.max(pagina, 0);
        int tamanoSeguro = Math.min(Math.max(tamano, 1), 100);

        String campo = "nombre";
        String direccionTexto = "asc";
        if (orden != null && !orden.isBlank()) {
            String[] partes = orden.split(",", 2);
            campo = partes[0].trim();
            if (partes.length > 1) {
                direccionTexto = partes[1].trim();
            }
        }
        String columnaSql = COLUMNAS_ORDEN_CATALOGO.get(campo);
        if (columnaSql == null) {
            throw new OrdenInvalidoException(campo);
        }
        String direccion = "desc".equalsIgnoreCase(direccionTexto) ? "DESC" : "ASC";

        String texto = buscar == null ? "" : buscar.trim();
        String filtros = "WHERE botica_id = :boticaId " +
                "AND (:vacio = true OR nombre ILIKE :patron OR laboratorio ILIKE :patron OR codigo_barras ILIKE :patron) ";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("vacio", texto.isEmpty())
                .addValue("patron", "%" + texto + "%")
                .addValue("limite", tamanoSeguro)
                .addValue("offset", (long) paginaSegura * tamanoSeguro);

        String sqlDatos = "SELECT * FROM productos " + filtros +
                "ORDER BY " + columnaSql + " " + direccion + ", id ASC LIMIT :limite OFFSET :offset";
        List<Producto> contenido = jdbc.query(sqlDatos, params, productoRowMapper);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM productos " + filtros, params, Long.class);
        return PaginaResponse.de(contenido, paginaSegura, tamanoSeguro, total == null ? 0L : total);
    }

    @Override
    public Long insertar(Producto producto) {
        String sql = "INSERT INTO productos (botica_id, nombre, laboratorio, categoria, codigo_barras, unidad_nombre, activo, creado_por) " +
                "VALUES (:boticaId, :nombre, :laboratorio, :categoria, :codigoBarras, :unidadNombre, true, :creadoPor) RETURNING id";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", producto.getBoticaId())
                .addValue("nombre", producto.getNombre())
                .addValue("laboratorio", producto.getLaboratorio())
                .addValue("categoria", producto.getCategoria())
                .addValue("codigoBarras", producto.getCodigoBarras())
                .addValue("unidadNombre", producto.getUnidadNombre())
                .addValue("creadoPor", producto.getCreadoPor());
        return jdbc.queryForObject(sql, params, Long.class);
    }

    @Override
    public void actualizar(Producto producto) {
        String sql = "UPDATE productos SET nombre = :nombre, laboratorio = :laboratorio, categoria = :categoria, " +
                "codigo_barras = :codigoBarras, unidad_nombre = :unidadNombre " +
                "WHERE botica_id = :boticaId AND id = :id";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", producto.getBoticaId())
                .addValue("id", producto.getId())
                .addValue("nombre", producto.getNombre())
                .addValue("laboratorio", producto.getLaboratorio())
                .addValue("categoria", producto.getCategoria())
                .addValue("codigoBarras", producto.getCodigoBarras())
                .addValue("unidadNombre", producto.getUnidadNombre());
        jdbc.update(sql, params);
    }

    @Override
    public void cambiarEstado(Long boticaId, Long productoId, boolean activo) {
        String sql = "UPDATE productos SET activo = :activo WHERE botica_id = :boticaId AND id = :id";
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("id", productoId)
                .addValue("activo", activo));
    }

    @Override
    public Long insertarPresentacion(PresentacionProducto presentacion) {
        String sql = "INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio, activo) " +
                "VALUES (:boticaId, :productoId, :etiqueta, :factor, :precio, true) RETURNING id";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", presentacion.getBoticaId())
                .addValue("productoId", presentacion.getProductoId())
                .addValue("etiqueta", presentacion.getEtiqueta())
                .addValue("factor", presentacion.getFactorConversion())
                .addValue("precio", presentacion.getPrecio());
        return jdbc.queryForObject(sql, params, Long.class);
    }

    @Override
    public void actualizarPresentacion(PresentacionProducto presentacion) {
        String sql = "UPDATE presentaciones SET etiqueta = :etiqueta, precio = :precio, activo = :activo " +
                "WHERE botica_id = :boticaId AND producto_id = :productoId AND id = :id";
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("boticaId", presentacion.getBoticaId())
                .addValue("productoId", presentacion.getProductoId())
                .addValue("id", presentacion.getId())
                .addValue("etiqueta", presentacion.getEtiqueta())
                .addValue("precio", presentacion.getPrecio())
                .addValue("activo", presentacion.getActivo()));
    }
}
