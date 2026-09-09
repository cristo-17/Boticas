package com.botica.backend.dao.impl;

import com.botica.backend.dao.MermaDao;
import com.botica.backend.dto.MermaResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.exception.OrdenInvalidoException;
import com.botica.backend.model.Merma;
import com.botica.backend.model.MovimientoStock;
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
 * Único DAO que sabe SQL de mermas. Todo WHERE incluye botica_id
 * (Regla FILTRADO POR BOTICA). El lote a dar de baja lo elige el
 * usuario (no FEFO, a diferencia de una venta): bloquearLoteConPrecio
 * bloquea EXACTAMENTE ese lote con FOR UPDATE.
 */
@Repository
public class MermaDaoJdbc implements MermaDao {

    private static final Map<String, String> COLUMNAS_ORDEN = Map.of(
            "fecha", "fecha",
            "cantidad", "cantidad",
            "valorVenta", "valor_venta"
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final MermaResponseRowMapper mermaResponseRowMapper = new MermaResponseRowMapper();

    public MermaDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LoteParaMerma> bloquearLoteConPrecio(Long boticaId, Long loteId) {
        // FOR UPDATE OF l: el LEFT JOIN a presentaciones no se puede bloquear
        // (Postgres lo prohíbe del lado nullable de un outer join) y no hace
        // falta -- solo el lote cambia stock acá.
        String sql = "SELECT l.id, l.producto_id, p.nombre AS producto_nombre, l.codigo, l.stock, pr.precio AS precio_unitario " +
                "FROM lotes l " +
                "JOIN productos p ON p.id = l.producto_id " +
                "LEFT JOIN presentaciones pr ON pr.producto_id = l.producto_id AND pr.factor_conversion = 1 " +
                "WHERE l.botica_id = :boticaId AND l.id = :loteId " +
                "FOR UPDATE OF l";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("loteId", loteId);
        return jdbc.query(sql, params, (rs, rowNum) -> new LoteParaMerma(
                rs.getLong("id"),
                rs.getLong("producto_id"),
                rs.getString("producto_nombre"),
                rs.getString("codigo"),
                rs.getInt("stock"),
                rs.getBigDecimal("precio_unitario")
        )).stream().findFirst();
    }

    @Override
    public Merma insertar(Merma merma) {
        String sql = "INSERT INTO mermas (botica_id, lote_id, usuario_id, cantidad, motivo, observacion, valor_venta) " +
                "VALUES (:boticaId, :loteId, :usuarioId, :cantidad, :motivo, :observacion, :valorVenta)";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", merma.getBoticaId())
                .addValue("loteId", merma.getLoteId())
                .addValue("usuarioId", merma.getUsuarioId())
                .addValue("cantidad", merma.getCantidad())
                .addValue("motivo", merma.getMotivo())
                .addValue("observacion", merma.getObservacion())
                .addValue("valorVenta", merma.getValorVenta());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        Long id = keyHolder.getKey().longValue();
        merma.setId(id);
        // fecha la pone el DEFAULT now() de la columna -- se relee para que la respuesta lleve el instante real, no una aproximación del cliente.
        merma.setFecha(jdbc.queryForObject("SELECT fecha FROM mermas WHERE id = :id",
                new MapSqlParameterSource("id", id), java.time.OffsetDateTime.class));
        return merma;
    }

    @Override
    public void insertarMovimientoStock(MovimientoStock movimiento) {
        String sql = "INSERT INTO movimientos_stock (botica_id, lote_id, tipo, cantidad, origen_captura, referencia_merma_id, creado_por) " +
                "VALUES (:boticaId, :loteId, :tipo, :cantidad, :origenCaptura, :referenciaMermaId, :creadoPor)";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", movimiento.getBoticaId())
                .addValue("loteId", movimiento.getLoteId())
                .addValue("tipo", movimiento.getTipo())
                .addValue("cantidad", movimiento.getCantidad())
                .addValue("origenCaptura", movimiento.getOrigenCaptura())
                .addValue("referenciaMermaId", movimiento.getReferenciaMermaId())
                .addValue("creadoPor", movimiento.getCreadoPor());
        jdbc.update(sql, params);
    }

    @Override
    public PaginaResponse<MermaResponse> listarPaginado(Long boticaId, LocalDate fecha, int pagina, int tamano, String orden) {
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
        String orderBy = "m." + columnaSql + " " + direccion + ", m.id ASC";

        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("fecha", fecha)
                .addValue("limite", tamanoSeguro)
                .addValue("offset", paginaSegura * tamanoSeguro);

        String selectBase = "FROM mermas m " +
                "JOIN lotes l ON l.id = m.lote_id " +
                "JOIN productos p ON p.id = l.producto_id " +
                "WHERE m.botica_id = :boticaId AND m.fecha::date = :fecha ";

        String sqlDatos = "SELECT m.id, m.lote_id, p.nombre AS producto_nombre, l.codigo AS lote_codigo, " +
                "m.cantidad, m.motivo, m.observacion, m.valor_venta, m.usuario_id, m.fecha " +
                selectBase + "ORDER BY " + orderBy + " LIMIT :limite OFFSET :offset";
        List<MermaResponse> contenido = jdbc.query(sqlDatos, params, mermaResponseRowMapper);

        String sqlCount = "SELECT COUNT(*) " + selectBase;
        Long total = jdbc.queryForObject(sqlCount, params, Long.class);

        return PaginaResponse.de(contenido, paginaSegura, tamanoSeguro, total == null ? 0L : total);
    }
}
