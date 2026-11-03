package com.botica.backend.dao.impl;

import com.botica.backend.dao.ReporteDao;
import com.botica.backend.dto.EgresoPorMotivoResponse;
import com.botica.backend.dto.FilaResultadoResponse;
import com.botica.backend.dto.TotalesResultadoResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Consultas SQL del reporte de resultados (D-E2-3).
 *
 * <ul>
 *   <li>Toda la aritmética monetaria corre en PostgreSQL sobre columnas {@code NUMERIC}.</li>
 *   <li>Las marcas de tiempo ({@code ventas.fecha}, {@code mermas.fecha}, {@code movimientos_caja.creado_en})
 *       se agrupan por día calendario de Lima ({@code (columna AT TIME ZONE 'America/Lima')::date}).</li>
 *   <li>Con {@code generate_series}, todos los días del rango {@code [desde, hasta]} aparecen
 *       (en {@code 0.00} cuando no hubo movimientos).</li>
 *   <li>Semana (Bloque C): lunes a domingo sobre la fecha de Lima
 *       ({@code date_trunc('week', dia::timestamp)}, ISO). Como {@code base_diaria} solo tiene
 *       días del rango, una semana cortada por el rango suma solo sus días reales y se rotula
 *       con ellos ({@code MIN(dia)} al {@code MAX(dia)}).</li>
 *   <li>{@code gastos} = costo de ventas + mermas a costo + egresos, calculado aquí (R19).</li>
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
                       date_trunc('week', d.dia::timestamp)::date AS semana,
                       COALESCE(v.ventas, 0.00)::numeric(12, 2) AS ventas,
                       COALESCE(v.costo_ventas, 0.00)::numeric(12, 2) AS costo_ventas,
                       (COALESCE(v.ventas, 0.00) - COALESCE(v.costo_ventas, 0.00))::numeric(12, 2) AS margen_bruto,
                       COALESCE(m.mermas_costo, 0.00)::numeric(12, 2) AS mermas_costo,
                       COALESCE(e.egresos, 0.00)::numeric(12, 2) AS egresos,
                       (COALESCE(v.costo_ventas, 0.00) + COALESCE(m.mermas_costo, 0.00)
                        + COALESCE(e.egresos, 0.00))::numeric(12, 2) AS gastos,
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
                   to_char(dia, 'DD/MM/YYYY') AS etiqueta,
                   ventas,
                   costo_ventas,
                   margen_bruto,
                   mermas_costo,
                   egresos,
                   gastos,
                   resultado
            FROM base_diaria
            ORDER BY periodo_dia ASC
            """;

    private static final String SQL_POR_MES = CTE_BASE_DIARIA + """
            SELECT periodo_mes AS periodo,
                   to_char(MIN(dia), 'MM/YYYY') AS etiqueta,
                   SUM(ventas)::numeric(12, 2) AS ventas,
                   SUM(costo_ventas)::numeric(12, 2) AS costo_ventas,
                   SUM(margen_bruto)::numeric(12, 2) AS margen_bruto,
                   SUM(mermas_costo)::numeric(12, 2) AS mermas_costo,
                   SUM(egresos)::numeric(12, 2) AS egresos,
                   SUM(gastos)::numeric(12, 2) AS gastos,
                   SUM(resultado)::numeric(12, 2) AS resultado
            FROM base_diaria
            GROUP BY periodo_mes
            ORDER BY periodo_mes ASC
            """;

    private static final String SQL_POR_SEMANA = CTE_BASE_DIARIA + """
            SELECT to_char(MIN(dia), 'YYYY-MM-DD') AS periodo,
                   to_char(MIN(dia), 'DD/MM') || ' al ' || to_char(MAX(dia), 'DD/MM') AS etiqueta,
                   SUM(ventas)::numeric(12, 2) AS ventas,
                   SUM(costo_ventas)::numeric(12, 2) AS costo_ventas,
                   SUM(margen_bruto)::numeric(12, 2) AS margen_bruto,
                   SUM(mermas_costo)::numeric(12, 2) AS mermas_costo,
                   SUM(egresos)::numeric(12, 2) AS egresos,
                   SUM(gastos)::numeric(12, 2) AS gastos,
                   SUM(resultado)::numeric(12, 2) AS resultado
            FROM base_diaria
            GROUP BY semana
            ORDER BY semana ASC
            """;

    /** Lista blanca agrupación → SQL (el servicio ya validó el valor). */
    private static final Map<String, String> SQL_POR_AGRUPACION = Map.of(
            "dia", SQL_POR_DIA,
            "semana", SQL_POR_SEMANA,
            "mes", SQL_POR_MES);

    private static final String SQL_EGRESOS_POR_MOTIVO = """
            SELECT mc.descripcion AS motivo,
                   COUNT(*) AS cantidad,
                   SUM(ABS(mc.monto))::numeric(12, 2) AS total
            FROM movimientos_caja mc
            WHERE mc.botica_id = :boticaId
              AND mc.tipo = 'egreso'
              AND (mc.creado_en AT TIME ZONE 'America/Lima')::date BETWEEN :desde AND :hasta
            GROUP BY mc.descripcion
            ORDER BY total DESC, motivo ASC
            """;

    private static final String SQL_NOMBRE_BOTICA = "SELECT nombre FROM boticas WHERE id = :boticaId";

    private static final String SQL_TOTALES = CTE_BASE_DIARIA + """
            SELECT COALESCE(SUM(ventas), 0.00)::numeric(12, 2) AS ventas,
                   COALESCE(SUM(costo_ventas), 0.00)::numeric(12, 2) AS costo_ventas,
                   COALESCE(SUM(margen_bruto), 0.00)::numeric(12, 2) AS margen_bruto,
                   COALESCE(SUM(mermas_costo), 0.00)::numeric(12, 2) AS mermas_costo,
                   COALESCE(SUM(egresos), 0.00)::numeric(12, 2) AS egresos,
                   COALESCE(SUM(gastos), 0.00)::numeric(12, 2) AS gastos,
                   COALESCE(SUM(resultado), 0.00)::numeric(12, 2) AS resultado
            FROM base_diaria
            """;

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<FilaResultadoResponse> MAPPER_FILA = (rs, rowNum) -> new FilaResultadoResponse(
            rs.getString("periodo"),
            rs.getString("etiqueta"),
            rs.getBigDecimal("ventas"),
            rs.getBigDecimal("costo_ventas"),
            rs.getBigDecimal("margen_bruto"),
            rs.getBigDecimal("mermas_costo"),
            rs.getBigDecimal("egresos"),
            rs.getBigDecimal("gastos"),
            rs.getBigDecimal("resultado")
    );

    private static final RowMapper<TotalesResultadoResponse> MAPPER_TOTALES = (rs, rowNum) -> new TotalesResultadoResponse(
            rs.getBigDecimal("ventas"),
            rs.getBigDecimal("costo_ventas"),
            rs.getBigDecimal("margen_bruto"),
            rs.getBigDecimal("mermas_costo"),
            rs.getBigDecimal("egresos"),
            rs.getBigDecimal("gastos"),
            rs.getBigDecimal("resultado")
    );

    private static final RowMapper<EgresoPorMotivoResponse> MAPPER_EGRESO_MOTIVO = (rs, rowNum) -> new EgresoPorMotivoResponse(
            rs.getString("motivo"),
            rs.getLong("cantidad"),
            rs.getBigDecimal("total")
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
        String sql = SQL_POR_AGRUPACION.get(agrupar);
        if (sql == null) {
            throw new IllegalArgumentException("Agrupación no soportada: " + agrupar);
        }
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

    @Override
    public List<EgresoPorMotivoResponse> consultarEgresosPorMotivo(
            Long boticaId, LocalDate desde, LocalDate hasta) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta);
        return jdbc.query(SQL_EGRESOS_POR_MOTIVO, params, MAPPER_EGRESO_MOTIVO);
    }

    @Override
    public String consultarNombreBotica(Long boticaId) {
        return jdbc.queryForObject(SQL_NOMBRE_BOTICA,
                new MapSqlParameterSource("boticaId", boticaId), String.class);
    }
}
