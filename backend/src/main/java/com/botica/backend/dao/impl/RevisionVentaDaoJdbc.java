package com.botica.backend.dao.impl;

import com.botica.backend.dao.RevisionVentaDao;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.model.RevisionVenta;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Único DAO que sabe SQL de revisiones_venta (Tarea 22). Todo WHERE incluye botica_id. */
@Repository
public class RevisionVentaDaoJdbc implements RevisionVentaDao {

    /** Columnas del listado: la revisión, quién la resolvió y los datos de su venta. */
    private static final String SELECT_CON_VENTA = "SELECT r.*, ur.nombre AS resuelta_por_nombre, " +
            "v.fecha AS venta_fecha, v.total AS venta_total, uv.nombre AS vendedor_nombre " +
            "FROM revisiones_venta r " +
            "JOIN ventas v ON v.id = r.venta_id AND v.botica_id = r.botica_id " +
            "JOIN usuarios uv ON uv.id = v.usuario_id " +
            "LEFT JOIN usuarios ur ON ur.id = r.resuelta_por ";

    private final NamedParameterJdbcTemplate jdbc;
    private final RevisionVentaRowMapper rowMapper = new RevisionVentaRowMapper();

    public RevisionVentaDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RevisionVenta insertar(RevisionVenta r) {
        String sql = "INSERT INTO revisiones_venta (botica_id, venta_id, venta_detalle_id, tipo, detalle, precio_cobrado, " +
                "precio_vigente, unidades_faltantes, creado_por) VALUES (:boticaId, :ventaId, :ventaDetalleId, :tipo, :detalle, " +
                ":precioCobrado, :precioVigente, :unidadesFaltantes, :creadoPor)";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", r.getBoticaId())
                .addValue("ventaId", r.getVentaId())
                .addValue("ventaDetalleId", r.getVentaDetalleId())
                .addValue("tipo", r.getTipo())
                .addValue("detalle", r.getDetalle())
                .addValue("precioCobrado", r.getPrecioCobrado())
                .addValue("precioVigente", r.getPrecioVigente())
                .addValue("unidadesFaltantes", r.getUnidadesFaltantes())
                .addValue("creadoPor", r.getCreadoPor());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        Long id = keyHolder.getKey().longValue();
        return jdbc.query(SELECT_CON_VENTA + "WHERE r.id = :id", new MapSqlParameterSource("id", id), rowMapper)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("La revisión recién insertada no se pudo releer, id=" + id));
    }

    @Override
    public List<RevisionVenta> listarPorVenta(Long boticaId, Long ventaId) {
        String sql = SELECT_CON_VENTA + "WHERE r.botica_id = :boticaId AND r.venta_id = :ventaId ORDER BY r.id ASC";
        var params = new MapSqlParameterSource().addValue("boticaId", boticaId).addValue("ventaId", ventaId);
        return jdbc.query(sql, params, rowMapper);
    }

    @Override
    public PaginaResponse<RevisionVenta> listarPaginado(Long boticaId, String estado, int pagina, int tamano) {
        int paginaSegura = Math.max(pagina, 0);
        int tamanoSeguro = Math.min(Math.max(tamano, 1), 100);
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("estado", estado)
                .addValue("limite", tamanoSeguro)
                .addValue("offset", paginaSegura * tamanoSeguro);
        String where = "WHERE r.botica_id = :boticaId AND (CAST(:estado AS VARCHAR) IS NULL OR r.estado = :estado) ";
        List<RevisionVenta> contenido = jdbc.query(SELECT_CON_VENTA + where +
                "ORDER BY r.creado_en DESC, r.id DESC LIMIT :limite OFFSET :offset", params, rowMapper);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM revisiones_venta r " + where, params, Long.class);
        return PaginaResponse.de(contenido, paginaSegura, tamanoSeguro, total == null ? 0L : total);
    }

    @Override
    public Optional<RevisionVenta> bloquearPorId(Long boticaId, Long id) {
        // FOR UPDATE OF r: solo la fila de la revisión, no la venta ni los usuarios del JOIN.
        String sql = SELECT_CON_VENTA + "WHERE r.botica_id = :boticaId AND r.id = :id FOR UPDATE OF r";
        var params = new MapSqlParameterSource().addValue("boticaId", boticaId).addValue("id", id);
        return jdbc.query(sql, params, rowMapper).stream().findFirst();
    }

    @Override
    public void resolver(Long boticaId, Long id, String resolucion, String nota, Long resueltaPor) {
        String sql = "UPDATE revisiones_venta SET estado = 'RESUELTA', resolucion = :resolucion, nota_resolucion = :nota, " +
                "resuelta_por = :resueltaPor, resuelta_en = now() WHERE botica_id = :boticaId AND id = :id AND estado = 'PENDIENTE'";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("id", id)
                .addValue("resolucion", resolucion)
                .addValue("nota", nota)
                .addValue("resueltaPor", resueltaPor);
        jdbc.update(sql, params);
    }

    @Override
    public long contarVentasConPendientes(Long boticaId) {
        Long n = jdbc.queryForObject("SELECT COUNT(DISTINCT venta_id) FROM revisiones_venta " +
                "WHERE botica_id = :boticaId AND estado = 'PENDIENTE'", new MapSqlParameterSource("boticaId", boticaId), Long.class);
        return n == null ? 0L : n;
    }
}
