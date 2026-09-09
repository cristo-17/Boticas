package com.botica.backend.dao.impl;

import com.botica.backend.dao.VentaDao;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.exception.OrdenInvalidoException;
import com.botica.backend.model.MovimientoStock;
import com.botica.backend.model.Venta;
import com.botica.backend.model.VentaDetalle;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Único DAO que sabe SQL de ventas/venta_detalle/movimientos_stock.
 * Todo WHERE incluye botica_id (Regla FILTRADO POR BOTICA, Tarea 8).
 */
@Repository
public class VentaDaoJdbc implements VentaDao {

    private static final Map<String, String> COLUMNAS_ORDEN = Map.of(
            "fecha", "fecha",
            "total", "total"
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final VentaRowMapper ventaRowMapper = new VentaRowMapper();
    private final VentaDetalleRowMapper ventaDetalleRowMapper = new VentaDetalleRowMapper();

    public VentaDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Venta> buscarPorClaveIdempotencia(Long boticaId, UUID clave) {
        String sql = "SELECT * FROM ventas WHERE botica_id = :boticaId AND clave_idempotencia = :clave";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("clave", clave);
        return jdbc.query(sql, params, ventaRowMapper).stream().findFirst();
    }

    @Override
    public Venta insertarCabecera(Venta venta) {
        String sql = "INSERT INTO ventas (botica_id, usuario_id, caja_id, subtotal, igv, igv_tasa, total, metodo_pago, clave_idempotencia, sincronizada) " +
                "VALUES (:boticaId, :usuarioId, :cajaId, :subtotal, :igv, :igvTasa, :total, :metodoPago, :claveIdempotencia, :sincronizada)";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", venta.getBoticaId())
                .addValue("usuarioId", venta.getUsuarioId())
                .addValue("cajaId", venta.getCajaId())
                .addValue("subtotal", venta.getSubtotal())
                .addValue("igv", venta.getIgv())
                .addValue("igvTasa", venta.getIgvTasa())
                .addValue("total", venta.getTotal())
                .addValue("metodoPago", venta.getMetodoPago())
                .addValue("claveIdempotencia", venta.getClaveIdempotencia())
                .addValue("sincronizada", venta.isSincronizada());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        Long id = keyHolder.getKey().longValue();
        String sqlReleer = "SELECT * FROM ventas WHERE id = :id";
        return jdbc.query(sqlReleer, new MapSqlParameterSource("id", id), ventaRowMapper).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("La venta recién insertada no se pudo releer, id=" + id));
    }

    @Override
    public VentaDetalle insertarDetalle(VentaDetalle detalle) {
        String sql = "INSERT INTO venta_detalle (venta_id, botica_id, producto_id, presentacion_id, lote_id, " +
                "nombre_producto, etiqueta_presentacion, cantidad, precio_unitario, costo_unitario, total_linea, origen_captura) " +
                "VALUES (:ventaId, :boticaId, :productoId, :presentacionId, :loteId, :nombreProducto, :etiquetaPresentacion, " +
                ":cantidad, :precioUnitario, :costoUnitario, :totalLinea, :origenCaptura)";
        var params = new MapSqlParameterSource()
                .addValue("ventaId", detalle.getVentaId())
                .addValue("boticaId", detalle.getBoticaId())
                .addValue("productoId", detalle.getProductoId())
                .addValue("presentacionId", detalle.getPresentacionId())
                .addValue("loteId", detalle.getLoteId())
                .addValue("nombreProducto", detalle.getNombreProducto())
                .addValue("etiquetaPresentacion", detalle.getEtiquetaPresentacion())
                .addValue("cantidad", detalle.getCantidad())
                .addValue("precioUnitario", detalle.getPrecioUnitario())
                .addValue("costoUnitario", detalle.getCostoUnitario())
                .addValue("totalLinea", detalle.getTotalLinea())
                .addValue("origenCaptura", detalle.getOrigenCaptura());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        detalle.setId(keyHolder.getKey().longValue());
        return detalle;
    }

    @Override
    public void insertarMovimientoStock(MovimientoStock movimiento) {
        String sql = "INSERT INTO movimientos_stock (botica_id, lote_id, tipo, cantidad, origen_captura, referencia_venta_id, referencia_merma_id, creado_por) " +
                "VALUES (:boticaId, :loteId, :tipo, :cantidad, :origenCaptura, :referenciaVentaId, :referenciaMermaId, :creadoPor)";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", movimiento.getBoticaId())
                .addValue("loteId", movimiento.getLoteId())
                .addValue("tipo", movimiento.getTipo())
                .addValue("cantidad", movimiento.getCantidad())
                .addValue("origenCaptura", movimiento.getOrigenCaptura())
                .addValue("referenciaVentaId", movimiento.getReferenciaVentaId())
                .addValue("referenciaMermaId", movimiento.getReferenciaMermaId())
                .addValue("creadoPor", movimiento.getCreadoPor());
        jdbc.update(sql, params);
    }

    @Override
    public List<VentaDetalle> listarDetallePorVenta(Long boticaId, Long ventaId) {
        String sql = "SELECT * FROM venta_detalle WHERE botica_id = :boticaId AND venta_id = :ventaId ORDER BY id ASC";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("ventaId", ventaId);
        return jdbc.query(sql, params, ventaDetalleRowMapper);
    }

    @Override
    public PaginaResponse<Venta> listarPaginado(Long boticaId, int pagina, int tamano, String orden) {
        int paginaSegura = Math.max(pagina, 0);
        int tamanoSeguro = Math.min(Math.max(tamano, 1), 100);

        String campo = "fecha";
        String direccionTexto = "desc";
        if (orden != null && !orden.isBlank()) {
            String[] partes = orden.split(",", 2);
            campo = partes[0].trim();
            if (partes.length > 1) {
                direccionTexto = partes[1].trim();
            }
        }
        String columnaSql = COLUMNAS_ORDEN.get(campo);
        if (columnaSql == null) {
            throw new OrdenInvalidoException(campo);
        }
        String direccion = "asc".equalsIgnoreCase(direccionTexto) ? "ASC" : "DESC";
        String orderBy = columnaSql + " " + direccion + ", id ASC";

        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("limite", tamanoSeguro)
                .addValue("offset", paginaSegura * tamanoSeguro);

        String sqlDatos = "SELECT * FROM ventas WHERE botica_id = :boticaId " +
                "ORDER BY " + orderBy + " LIMIT :limite OFFSET :offset";
        List<Venta> contenido = jdbc.query(sqlDatos, params, ventaRowMapper);

        String sqlCount = "SELECT COUNT(*) FROM ventas WHERE botica_id = :boticaId";
        Long total = jdbc.queryForObject(sqlCount, params, Long.class);

        return PaginaResponse.de(contenido, paginaSegura, tamanoSeguro, total == null ? 0L : total);
    }
}
