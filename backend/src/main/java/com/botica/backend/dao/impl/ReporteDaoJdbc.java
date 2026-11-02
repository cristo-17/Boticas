package com.botica.backend.dao.impl;

import com.botica.backend.dao.ReporteDao;
import com.botica.backend.dto.FilaResultadoResponse;
import com.botica.backend.dto.TotalesResultadoResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Consultas SQL del reporte de resultados (D-E2-3).
 *
 * <ul>
 *   <li>Toda la aritmética monetaria corre en PostgreSQL sobre columnas {@code NUMERIC}.</li>
 *   <li>Las marcas de tiempo ({@code ventas.fecha}, {@code mermas.fecha}, {@code movimientos_caja.creado_en})
 *       se agrupan por día calendario de Lima ({@code (columna AT TIME ZONE 'America/Lima')::date}).</li>
 *   <li>Con {@code generate_series}, todos los días del rango {@code [desde, hasta]} aparecen
 *       (en {@code 0.00} cuando no hubo movimientos).</li>
 * </ul>
 */
@Repository
public class ReporteDaoJdbc implements ReporteDao {

    private static final String CTE_BASE_DIARIA = """
            WITH dias AS (
                SELECT gs::date AS dia
                FROM generate_series(CAST(:desde AS date), CAST(:hasta AS date), INTERVAL '1 day') AS gs
            ),
            ventas_dia AS (
                SELECT (v.fecha AT TIME ZONE 'America/Lima')::date AS dia,
                       SUM(vd.total_linea) AS ventas,
                       SUM(vd.cantidad * vd.costo_unitario) AS costo_ventas
                FROM ventas v
                JOIN venta_detalle vd ON vd.venta_id = v.id
                WHERE v.botica_id = :boticaId
                  AND (v.fecha AT TIME ZONE 'America/Lima')::date BETWEEN :desde AND :hasta
                GROUP BY (v.fecha AT TIME ZONE 'America/Lima')::date
            ),
            mermas_dia AS (
                SELECT (m.fecha AT TIME ZONE 'America/Lima')::date AS dia,
                       SUM(m.cantidad * l.costo_unitario) AS mermas_costo
                FROM mermas m
                JOIN lotes l ON l.id = m.lote_id AND l.botica_id = m.botica_id
                WHERE m.botica_id = :boticaId
                  AND (m.fecha AT TIME ZONE 'America/Lima')::date BETWEEN :desde AND :hasta
                GROUP BY (m.fecha AT TIME ZONE 'America/Lima')::date
            ),
            egresos_dia AS (
                SELECT (mc.creado_en AT TIME ZONE 'America/Lima')::date AS dia,
                       SUM(ABS(mc.monto)) AS egresos
                FROM movimientos_caja mc
                WHERE mc.botica_id = :boticaId
                  AND mc.tipo = 'egreso'
                  AND (mc.creado_en AT TIME ZONE 'America/Lima')::date BETWEEN :desde AND :hasta
                GROUP BY (mc.creado_en AT TIME ZONE 'America/Lima')::date
            ),
            base_diaria AS (
                SELECT d.dia,
                       to_char(d.dia, 'YYYY-MM-DD') AS periodo_dia,
                       to_char(d.dia, 'YYYY-MM') AS periodo_mes,
                       COALESCE(v.ventas, 0.00)::numeric(12, 2) AS ventas,
                       COALESCE(v.costo_ventas, 0.00)::numeric(12, 2) AS costo_ventas,
                       (COALESCE(v.ventas, 0.00) - COALESCE(v.costo_ventas, 0.00))::numeric(12, 2) AS margen_bruto,
                       COALESCE(m.mermas_costo, 0.00)::numeric(12, 2) AS mermas_costo,
                       COALESCE(e.egresos, 0.00)::numeric(12, 2) AS egresos,
                       (COALESCE(v.ventas, 0.00) - COALESCE(v.costo_ventas, 0.00)
                        - COALESCE(m.mermas_costo, 0.00) - COALESCE(e.egresos, 0.00))::numeric(12, 2) AS resultado
                FROM dias d
                LEFT JOIN ventas_dia v ON v.dia = d.dia
                LEFT JOIN mermas_dia m ON m.dia = d.dia
                LEFT JOIN egresos_dia e ON e.dia = d.dia
            )
            """;

    private static final String SQL_POR_DIA = CTE_BASE_DIARIA + """
            SELECT periodo_dia AS periodo,
                   ventas,
                   costo_ventas,
                   margen_bruto,
                   mermas_costo,
                   egresos,
                   resultado
            FROM base_diaria
            ORDER BY periodo_dia ASC
            """;

    private static final String SQL_POR_MES = CTE_BASE_DIARIA + """
            SELECT periodo_mes AS periodo,
                   SUM(ventas)::numeric(12, 2) AS ventas,
                   SUM(costo_ventas)::numeric(12, 2) AS costo_ventas,
                   SUM(margen_bruto)::numeric(12, 2) AS margen_bruto,
                   SUM(mermas_costo)::numeric(12, 2) AS mermas_costo,
                   SUM(egresos)::numeric(12, 2) AS egresos,
                   SUM(resultado)::numeric(12, 2) AS resultado
            FROM base_diaria
            GROUP BY periodo_mes
            ORDER BY periodo_mes ASC
            """;

    private static final String SQL_TOTALES = CTE_BASE_DIARIA + """
            SELECT COALESCE(SUM(ventas), 0.00)::numeric(12, 2) AS ventas,
                   COALESCE(SUM(costo_ventas), 0.00)::numeric(12, 2) AS costo_ventas,
                   COALESCE(SUM(margen_bruto), 0.00)::numeric(12, 2) AS margen_bruto,
                   COALESCE(SUM(mermas_costo), 0.00)::numeric(12, 2) AS mermas_costo,
                   COALESCE(SUM(egresos), 0.00)::numeric(12, 2) AS egresos,
                   COALESCE(SUM(resultado), 0.00)::numeric(12, 2) AS resultado
            FROM base_diaria
            """;

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<FilaResultadoResponse> MAPPER_FILA = (rs, rowNum) -> new FilaResultadoResponse(
            rs.getString("periodo"),
            rs.getBigDecimal("ventas"),
            rs.getBigDecimal("costo_ventas"),
            rs.getBigDecimal("margen_bruto"),
            rs.getBigDecimal("mermas_costo"),
            rs.getBigDecimal("egresos"),
            rs.getBigDecimal("resultado")
    );

    private static final RowMapper<TotalesResultadoResponse> MAPPER_TOTALES = (rs, rowNum) -> new TotalesResultadoResponse(
            rs.getBigDecimal("ventas"),
            rs.getBigDecimal("costo_ventas"),
            rs.getBigDecimal("margen_bruto"),
            rs.getBigDecimal("mermas_costo"),
            rs.getBigDecimal("egresos"),
            rs.getBigDecimal("resultado")
    );

    public ReporteDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FilaResultadoResponse> consultarFilasResultados(
            Long boticaId, LocalDate desde, LocalDate hasta, String agrupar) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta);
        String sql = "mes".equals(agrupar) ? SQL_POR_MES : SQL_POR_DIA;
        return jdbc.query(sql, params, MAPPER_FILA);
    }

    @Override
    public TotalesResultadoResponse consultarTotalesResultados(
            Long boticaId, LocalDate desde, LocalDate hasta) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta);
        return jdbc.queryForObject(SQL_TOTALES, params, MAPPER_TOTALES);
    }
}
