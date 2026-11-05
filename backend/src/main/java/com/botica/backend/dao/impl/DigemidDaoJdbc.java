package com.botica.backend.dao.impl;

import com.botica.backend.dao.DigemidDao;
import com.botica.backend.dto.FilaDigemidResponse;
import com.botica.backend.dto.TotalDigemidProductoResponse;
import com.botica.backend.dto.TotalesDigemidResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * SQL del reporte DIGEMID (RF-54). Se calcula al consultar, sin tabla propia
 * (reportes_digemid sigue vacía, Fase 3). Una fila por venta_detalle con
 * requiere_receta CONGELADO en true (nunca el valor actual del catálogo).
 * Lote y receta van con LEFT JOIN: una línea sin lote (venta sin conexión) y
 * una venta sin conexión que llegó sin receta también se listan. Todo JOIN y
 * WHERE lleva botica_id.
 */
@Repository
public class DigemidDaoJdbc implements DigemidDao {

    private static final String FROM_LINEAS = """
            FROM venta_detalle vd
            JOIN ventas v ON v.id = vd.venta_id AND v.botica_id = vd.botica_id
            JOIN productos p ON p.id = vd.producto_id AND p.botica_id = vd.botica_id
            JOIN usuarios u ON u.id = v.usuario_id
            LEFT JOIN lotes l ON l.id = vd.lote_id AND l.botica_id = vd.botica_id
            LEFT JOIN recetas r ON r.venta_id = v.id AND r.botica_id = v.botica_id
            WHERE vd.botica_id = :boticaId
              AND vd.requiere_receta
              AND v.fecha >= CAST(:inicio AS timestamptz)
              AND v.fecha < CAST(:fin AS timestamptz)
            """;

    private static final String SQL_LINEAS = """
            SELECT vd.id, v.fecha, v.id AS venta_id, vd.producto_id, vd.nombre_producto,
                   vd.etiqueta_presentacion, vd.cantidad, p.unidad_nombre,
                   vd.lote_id, l.codigo AS lote_codigo, l.fecha_vencimiento AS lote_vencimiento,
                   r.numero AS receta_numero, r.cmp AS receta_cmp, r.medico_nombre, r.fecha_emision,
                   u.nombre AS tecnico
            """ + FROM_LINEAS + """
            ORDER BY v.fecha, v.id, vd.id
            LIMIT CAST(:limite AS integer) OFFSET :offset
            """;

    private static final String SQL_POR_PRODUCTO = """
            SELECT vd.producto_id, p.nombre, p.unidad_nombre,
                   COUNT(DISTINCT r.id) AS recetas, SUM(vd.cantidad) AS unidades
            """ + FROM_LINEAS + """
            GROUP BY vd.producto_id, p.nombre, p.unidad_nombre
            ORDER BY p.nombre, vd.producto_id
            """;

    private static final String SQL_TOTALES = """
            SELECT COUNT(*) AS lineas, COUNT(DISTINCT r.id) AS recetas, COALESCE(SUM(vd.cantidad), 0) AS unidades
            """ + FROM_LINEAS;

    private final NamedParameterJdbcTemplate jdbc;
    private final FilaDigemidRowMapper filaRowMapper = new FilaDigemidRowMapper();
    private final TotalDigemidProductoRowMapper productoRowMapper = new TotalDigemidProductoRowMapper();
    private final TotalesDigemidRowMapper totalesRowMapper = new TotalesDigemidRowMapper();

    public DigemidDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FilaDigemidResponse> listarLineas(Long boticaId, OffsetDateTime inicio, OffsetDateTime fin,
                                                  Integer limite, long offset) {
        var params = parametros(boticaId, inicio, fin)
                .addValue("limite", limite, Types.INTEGER)
                .addValue("offset", offset);
        return jdbc.query(SQL_LINEAS, params, filaRowMapper);
    }

    @Override
    public List<TotalDigemidProductoResponse> totalesPorProducto(Long boticaId, OffsetDateTime inicio, OffsetDateTime fin) {
        return jdbc.query(SQL_POR_PRODUCTO, parametros(boticaId, inicio, fin), productoRowMapper);
    }

    @Override
    public TotalesDigemidResponse totales(Long boticaId, OffsetDateTime inicio, OffsetDateTime fin) {
        return jdbc.queryForObject(SQL_TOTALES, parametros(boticaId, inicio, fin), totalesRowMapper);
    }

    private static MapSqlParameterSource parametros(Long boticaId, OffsetDateTime inicio, OffsetDateTime fin) {
        return new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("inicio", inicio, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("fin", fin, Types.TIMESTAMP_WITH_TIMEZONE);
    }
}
