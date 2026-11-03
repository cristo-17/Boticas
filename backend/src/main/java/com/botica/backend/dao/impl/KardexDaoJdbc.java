package com.botica.backend.dao.impl;

import com.botica.backend.dao.KardexDao;
import com.botica.backend.dto.FilaKardexResponse;
import com.botica.backend.dto.ResumenKardexResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Único DAO que sabe SQL del Kardex (Tarea 20). Lee movimientos_stock (el
 * libro inmutable desde V10) unido a lotes, venta_detalle, recetas y usuarios.
 * Todo WHERE incluye botica_id.
 *
 * <p>El saldo acumulado es {@code SUM(delta) OVER (ORDER BY orden, fecha, id)}:
 * la fila SALDO_ANTERIOR (orden 0) lleva como delta la suma de todo lo
 * anterior a "desde", así que el saldo de la primera fila del rango ya parte
 * de ahí. La ventana se evalúa antes de LIMIT/OFFSET: cualquier página trae
 * el saldo correcto.</p>
 */
@Repository
public class KardexDaoJdbc implements KardexDao {

    /**
     * Movimientos del producto, ya clasificados. SALIDA_RECETA por línea: la
     * línea de la venta se registró con receta (venta_detalle.requiere_receta,
     * congelado al vender) y la venta tiene su receta guardada. Nunca el valor
     * actual de productos.requiere_receta: cambiar el catálogo no reescribe lo
     * ya dispensado. Un movimiento de venta y sus líneas comparten (venta, lote);
     * todas las líneas de un mismo lote son del mismo producto y se congelaron
     * con el mismo valor, así que bool_or no mezcla nada.
     */
    private static final String CTE_MOVIMIENTOS = """
            WITH movs AS (
                SELECT ms.id,
                       ms.creado_en AS fecha,
                       ms.cantidad,
                       ms.lote_id,
                       l.codigo AS lote_codigo,
                       ms.referencia_venta_id AS venta_id,
                       ms.referencia_merma_id AS merma_id,
                       CASE WHEN ms.tipo = 'VENTA' AND vd.con_receta THEN r.numero END AS receta_numero,
                       u.nombre AS usuario,
                       CASE WHEN ms.tipo = 'INGRESO' THEN 'ENTRADA'
                            WHEN ms.tipo = 'MERMA' THEN 'MERMA'
                            WHEN vd.con_receta AND r.id IS NOT NULL THEN 'SALIDA_RECETA'
                            ELSE 'SALIDA_LIBRE' END AS tipo,
                       (ms.tipo = 'INGRESO' AND ms.creado_por IS NULL) AS entrada_inicial
                FROM movimientos_stock ms
                JOIN lotes l ON l.id = ms.lote_id AND l.botica_id = ms.botica_id
                LEFT JOIN LATERAL (
                    SELECT bool_or(d.requiere_receta) AS con_receta
                    FROM venta_detalle d
                    WHERE d.venta_id = ms.referencia_venta_id AND d.lote_id = ms.lote_id
                      AND d.botica_id = ms.botica_id
                ) vd ON ms.tipo = 'VENTA'
                LEFT JOIN recetas r ON r.venta_id = ms.referencia_venta_id AND r.botica_id = ms.botica_id
                LEFT JOIN usuarios u ON u.id = ms.creado_por
                WHERE ms.botica_id = :boticaId AND l.producto_id = :productoId
            )
            """;

    private static final String EN_RANGO =
            "(CAST(:inicio AS timestamptz) IS NULL OR fecha >= CAST(:inicio AS timestamptz)) " +
            "AND (CAST(:fin AS timestamptz) IS NULL OR fecha < CAST(:fin AS timestamptz))";

    private static final String SQL_FILAS = CTE_MOVIMIENTOS + """
            , filas AS (
                SELECT 0 AS orden, NULL::bigint AS id, CAST(:inicio AS timestamptz) AS fecha,
                       'SALDO_ANTERIOR' AS tipo, false AS entrada_inicial,
                       COALESCE(SUM(cantidad), 0)::bigint AS delta, NULL::integer AS cantidad,
                       NULL::bigint AS lote_id, NULL::varchar AS lote_codigo, NULL::bigint AS venta_id,
                       NULL::bigint AS merma_id, NULL::varchar AS receta_numero, NULL::varchar AS usuario
                FROM movs
                WHERE fecha < CAST(:inicio AS timestamptz)
                HAVING CAST(:inicio AS timestamptz) IS NOT NULL
                UNION ALL
                SELECT 1, id, fecha, tipo, entrada_inicial, cantidad::bigint, cantidad,
                       lote_id, lote_codigo, venta_id, merma_id, receta_numero, usuario
                FROM movs
                WHERE """ + EN_RANGO + """

            )
            SELECT f.*, SUM(f.delta) OVER (ORDER BY f.orden, f.fecha, f.id ROWS UNBOUNDED PRECEDING) AS saldo
            FROM filas f
            ORDER BY f.orden, f.fecha, f.id
            LIMIT CAST(:limite AS integer) OFFSET :offset
            """;

    private static final String SQL_RESUMEN = CTE_MOVIMIENTOS + """
            , totales AS (
                SELECT COALESCE(SUM(cantidad) FILTER (WHERE fecha < CAST(:inicio AS timestamptz)), 0) AS saldo_inicial,
                       COALESCE(SUM(cantidad) FILTER (WHERE cantidad > 0 AND """ + EN_RANGO + """
            ), 0) AS entradas,
                       COALESCE(-SUM(cantidad) FILTER (WHERE cantidad < 0 AND """ + EN_RANGO + """
            ), 0) AS salidas,
                       COUNT(*) FILTER (WHERE """ + EN_RANGO + """
            ) AS movimientos
                FROM movs
            )
            SELECT t.saldo_inicial, t.entradas, t.salidas,
                   t.saldo_inicial + t.entradas - t.salidas AS saldo_final,
                   (SELECT COALESCE(SUM(stock), 0) FROM lotes
                    WHERE botica_id = :boticaId AND producto_id = :productoId) AS stock_actual,
                   t.movimientos
            FROM totales t
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final FilaKardexRowMapper filaRowMapper = new FilaKardexRowMapper();
    private final ResumenKardexRowMapper resumenRowMapper = new ResumenKardexRowMapper();

    public KardexDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FilaKardexResponse> listarFilas(Long boticaId, Long productoId, OffsetDateTime inicio, OffsetDateTime fin,
                                                Integer limite, long offset) {
        var params = parametros(boticaId, productoId, inicio, fin)
                .addValue("limite", limite, Types.INTEGER)
                .addValue("offset", offset);
        return jdbc.query(SQL_FILAS, params, filaRowMapper);
    }

    @Override
    public ResumenKardexResponse resumir(Long boticaId, Long productoId, OffsetDateTime inicio, OffsetDateTime fin) {
        return jdbc.queryForObject(SQL_RESUMEN, parametros(boticaId, productoId, inicio, fin), resumenRowMapper);
    }

    private static MapSqlParameterSource parametros(Long boticaId, Long productoId, OffsetDateTime inicio, OffsetDateTime fin) {
        return new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("productoId", productoId)
                .addValue("inicio", inicio, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("fin", fin, Types.TIMESTAMP_WITH_TIMEZONE);
    }
}
