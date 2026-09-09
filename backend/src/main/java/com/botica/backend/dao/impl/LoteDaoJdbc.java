package com.botica.backend.dao.impl;

import com.botica.backend.dao.LoteDao;
import com.botica.backend.dto.LoteResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.exception.FiltroInvalidoException;
import com.botica.backend.exception.OrdenInvalidoException;
import com.botica.backend.model.Lote;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Único DAO que sabe SQL de lotes. Todo WHERE incluye botica_id (Regla
 * FILTRADO POR BOTICA, Tarea 8). estadoVencimiento/stockEstado se
 * calculan en SQL con los MISMOS parámetros que filtran — filtro y
 * estado mostrado no pueden discrepar nunca (docs/API-CONTRATO.md).
 */
@Repository
public class LoteDaoJdbc implements LoteDao {

    /** Lista blanca de columnas ordenables (Anexo D). */
    private static final Map<String, String> COLUMNAS_ORDEN = Map.of(
            "fechaVencimiento", "l.fecha_vencimiento",
            "stock", "l.stock",
            "codigo", "l.codigo",
            "productoNombre", "p.nombre"
    );

    private static final String SELECT_BASE =
            "SELECT l.*, p.nombre AS producto_nombre, p.categoria AS categoria, pr.precio AS precio_unitario, " +
                    "CASE WHEN l.fecha_vencimiento < :hoy THEN 'VENCIDO' " +
                    "     WHEN l.fecha_vencimiento <= :hoyMasCritico THEN 'CRITICO' " +
                    "     WHEN l.fecha_vencimiento <= :hoyMasAdvertencia THEN 'ADVERTENCIA' " +
                    "     ELSE 'OK' END AS estado_vencimiento, " +
                    "CASE WHEN l.stock = 0 THEN 'AGOTADO' " +
                    "     WHEN l.stock <= :umbralStockBajo THEN 'CRITICO' " +
                    "     ELSE 'OK' END AS stock_estado " +
                    "FROM lotes l " +
                    "JOIN productos p ON p.id = l.producto_id " +
                    "LEFT JOIN presentaciones pr ON pr.producto_id = l.producto_id AND pr.factor_conversion = 1 " +
                    "WHERE l.botica_id = :boticaId ";

    private final NamedParameterJdbcTemplate jdbc;
    private final LoteResponseRowMapper loteResponseRowMapper = new LoteResponseRowMapper();
    private final LoteRowMapper loteRowMapper = new LoteRowMapper();

    public LoteDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PaginaResponse<LoteResponse> listarPaginado(
            Long boticaId, String categoria, String vencimiento, boolean soloStockBajo, Long productoId,
            int pagina, int tamano, String orden,
            LocalDate hoy, int criticoDias, int advertenciaDias, int umbralStockBajo
    ) {
        int paginaSegura = Math.max(pagina, 0);
        int tamanoSeguro = Math.min(Math.max(tamano, 1), 100);

        String campo = "fechaVencimiento";
        String direccionTexto = "asc";
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
        String direccion = "desc".equalsIgnoreCase(direccionTexto) ? "DESC" : "ASC";
        String orderBy = columnaSql + " " + direccion + ", l.id ASC";

        var params = parametrosEstado(boticaId, hoy, criticoDias, advertenciaDias, umbralStockBajo)
                .addValue("limite", tamanoSeguro)
                .addValue("offset", paginaSegura * tamanoSeguro);

        StringBuilder filtros = new StringBuilder();
        if (categoria != null && !categoria.isBlank() && !"Todas".equalsIgnoreCase(categoria)) {
            filtros.append("AND p.categoria = :categoria ");
            params.addValue("categoria", categoria);
        }
        if (productoId != null) {
            filtros.append("AND l.producto_id = :productoId ");
            params.addValue("productoId", productoId);
        }
        if (soloStockBajo) {
            filtros.append("AND l.stock <= :umbralStockBajo ");
        }
        if (vencimiento != null && !vencimiento.isBlank() && !"todos".equalsIgnoreCase(vencimiento)) {
            switch (vencimiento.toLowerCase()) {
                case "vencido" -> filtros.append("AND l.fecha_vencimiento < :hoy ");
                case "critico" -> filtros.append("AND l.fecha_vencimiento >= :hoy AND l.fecha_vencimiento <= :hoyMasCritico ");
                case "advertencia" -> filtros.append("AND l.fecha_vencimiento > :hoyMasCritico AND l.fecha_vencimiento <= :hoyMasAdvertencia ");
                case "ok" -> filtros.append("AND l.fecha_vencimiento > :hoyMasAdvertencia ");
                default -> throw new FiltroInvalidoException("vencimiento", vencimiento);
            }
        }

        String sqlDatos = SELECT_BASE + filtros + "ORDER BY " + orderBy + " LIMIT :limite OFFSET :offset";
        List<LoteResponse> contenido = jdbc.query(sqlDatos, params, loteResponseRowMapper);

        String sqlCount = "SELECT COUNT(*) FROM lotes l JOIN productos p ON p.id = l.producto_id WHERE l.botica_id = :boticaId " + filtros;
        Long total = jdbc.queryForObject(sqlCount, params, Long.class);

        return PaginaResponse.de(contenido, paginaSegura, tamanoSeguro, total == null ? 0L : total);
    }

    @Override
    public Lote insertar(Lote lote) {
        String sql = "INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, ubicacion, costo_unitario, creado_por) " +
                "VALUES (:boticaId, :productoId, :codigo, :fechaVencimiento, :stock, :ubicacion, :costoUnitario, :creadoPor)";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", lote.getBoticaId())
                .addValue("productoId", lote.getProductoId())
                .addValue("codigo", lote.getCodigo())
                .addValue("fechaVencimiento", lote.getFechaVencimiento())
                .addValue("stock", lote.getStock())
                .addValue("ubicacion", lote.getUbicacion())
                .addValue("costoUnitario", lote.getCostoUnitario())
                .addValue("creadoPor", lote.getCreadoPor());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        Long id = keyHolder.getKey().longValue();
        return buscarPorId(lote.getBoticaId(), id)
                .orElseThrow(() -> new IllegalStateException("El lote recién insertado no se pudo releer, id=" + id));
    }

    @Override
    public Optional<Lote> buscarPorId(Long boticaId, Long id) {
        String sql = "SELECT * FROM lotes WHERE botica_id = :boticaId AND id = :id";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("id", id);
        return jdbc.query(sql, params, loteRowMapper).stream().findFirst();
    }

    @Override
    public List<Lote> bloquearLotesFefo(Long boticaId, Long productoId) {
        String sql = "SELECT * FROM lotes WHERE botica_id = :boticaId AND producto_id = :productoId " +
                "AND stock > 0 ORDER BY fecha_vencimiento ASC, id ASC FOR UPDATE";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("productoId", productoId);
        return jdbc.query(sql, params, loteRowMapper);
    }

    @Override
    public void descontarStock(Long loteId, int cantidad) {
        String sql = "UPDATE lotes SET stock = stock - :cantidad WHERE id = :id";
        var params = new MapSqlParameterSource()
                .addValue("cantidad", cantidad)
                .addValue("id", loteId);
        jdbc.update(sql, params);
    }

    @Override
    public boolean existeProducto(Long boticaId, Long productoId) {
        String sql = "SELECT COUNT(*) FROM productos WHERE botica_id = :boticaId AND id = :productoId";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("productoId", productoId);
        Integer total = jdbc.queryForObject(sql, params, Integer.class);
        return total != null && total > 0;
    }

    @Override
    public Optional<LoteResponse> buscarResponsePorId(
            Long boticaId, Long id, LocalDate hoy, int criticoDias, int advertenciaDias, int umbralStockBajo
    ) {
        var params = parametrosEstado(boticaId, hoy, criticoDias, advertenciaDias, umbralStockBajo)
                .addValue("id", id);
        String sql = SELECT_BASE + "AND l.id = :id";
        return jdbc.query(sql, params, loteResponseRowMapper).stream().findFirst();
    }

    private MapSqlParameterSource parametrosEstado(Long boticaId, LocalDate hoy, int criticoDias, int advertenciaDias, int umbralStockBajo) {
        return new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("hoy", hoy)
                .addValue("hoyMasCritico", hoy.plusDays(criticoDias))
                .addValue("hoyMasAdvertencia", hoy.plusDays(advertenciaDias))
                .addValue("umbralStockBajo", umbralStockBajo);
    }
}
