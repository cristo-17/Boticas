package com.botica.backend.dao.impl;

import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ResumenCierreResponse;
import com.botica.backend.exception.OrdenInvalidoException;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.model.MovimientoCaja;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Único DAO que sabe SQL de caja. Todo WHERE incluye botica_id (Regla
 * FILTRADO POR BOTICA de la Tarea 8) — un WHERE sin ese filtro es una
 * fuga silenciosa entre boticas, no un error que se vea a simple vista.
 */
@Repository
public class CajaDaoJdbc implements CajaDao {

    private static final String SELECT_CAJA =
            "SELECT c.*, u.nombre AS usuario_nombre FROM caja_diaria c " +
                    "JOIN usuarios u ON u.id = c.usuario_id ";

    /** Lista blanca de columnas ordenables (Anexo D) — nunca se concatena el parámetro del cliente directo. */
    private static final Map<String, String> COLUMNAS_ORDEN_MOVIMIENTOS = Map.of(
            "creadoEn", "creado_en",
            "monto", "monto",
            "tipo", "tipo"
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final CajaDiariaRowMapper cajaRowMapper = new CajaDiariaRowMapper();
    private final MovimientoCajaRowMapper movimientoRowMapper = new MovimientoCajaRowMapper();

    public CajaDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CajaDiaria> buscarAbiertaDelUsuario(Long boticaId, Long usuarioId) {
        String sql = SELECT_CAJA + "WHERE c.botica_id = :boticaId AND c.usuario_id = :usuarioId AND c.estado = 'ABIERTA'";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("usuarioId", usuarioId);
        return jdbc.query(sql, params, cajaRowMapper).stream().findFirst();
    }

    @Override
    public boolean existeCajaAbierta(Long boticaId, Long usuarioId, String turno, LocalDate fecha) {
        String sql = "SELECT COUNT(*) FROM caja_diaria " +
                "WHERE botica_id = :boticaId AND usuario_id = :usuarioId " +
                "AND turno = :turno AND fecha = :fecha AND estado = 'ABIERTA'";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("usuarioId", usuarioId)
                .addValue("turno", turno)
                .addValue("fecha", fecha);
        Integer total = jdbc.queryForObject(sql, params, Integer.class);
        return total != null && total > 0;
    }

    @Override
    public CajaDiaria insertar(CajaDiaria caja) {
        String sql = "INSERT INTO caja_diaria (botica_id, usuario_id, fecha, turno, monto_apertura, hora_apertura, estado) " +
                "VALUES (:boticaId, :usuarioId, :fecha, :turno, :montoApertura, :horaApertura, 'ABIERTA')";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", caja.getBoticaId())
                .addValue("usuarioId", caja.getUsuarioId())
                .addValue("fecha", caja.getFecha())
                .addValue("turno", caja.getTurno())
                .addValue("montoApertura", caja.getMontoApertura())
                .addValue("horaApertura", caja.getHoraApertura());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        Long id = keyHolder.getKey().longValue();
        return buscarPorId(caja.getBoticaId(), id)
                .orElseThrow(() -> new IllegalStateException("La caja recién insertada no se pudo releer, id=" + id));
    }

    @Override
    public Optional<CajaDiaria> buscarPorId(Long boticaId, Long id) {
        String sql = SELECT_CAJA + "WHERE c.botica_id = :boticaId AND c.id = :id";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("id", id);
        return jdbc.query(sql, params, cajaRowMapper).stream().findFirst();
    }

    @Override
    public void actualizarCierre(CajaDiaria caja) {
        String sql = "UPDATE caja_diaria SET monto_contado = :montoContado, monto_esperado = :montoEsperado, " +
                "diferencia = :diferencia, semaforo_descuadre = :semaforo, hora_cierre = :horaCierre, " +
                "observaciones = :observaciones, estado = 'CERRADA' " +
                "WHERE id = :id AND botica_id = :boticaId";
        var params = new MapSqlParameterSource()
                .addValue("montoContado", caja.getMontoContado())
                .addValue("montoEsperado", caja.getMontoEsperado())
                .addValue("diferencia", caja.getDiferencia())
                .addValue("semaforo", caja.getSemaforoDescuadre())
                .addValue("horaCierre", caja.getHoraCierre())
                .addValue("observaciones", caja.getObservaciones())
                .addValue("id", caja.getId())
                .addValue("boticaId", caja.getBoticaId());
        jdbc.update(sql, params);
    }

    @Override
    public BigDecimal sumarMovimientosEfectivo(Long boticaId, Long cajaId) {
        String sql = "SELECT COALESCE(SUM(monto), 0) FROM movimientos_caja " +
                "WHERE botica_id = :boticaId AND caja_id = :cajaId AND afecta_efectivo = true";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("cajaId", cajaId);
        return jdbc.queryForObject(sql, params, BigDecimal.class);
    }

    @Override
    public ResumenCierreResponse calcularResumen(Long boticaId, Long cajaId) {
        String sqlVentas = "SELECT " +
                "COALESCE(SUM(total) FILTER (WHERE metodo_pago = 'efectivo'), 0) AS total_efectivo, " +
                "COALESCE(SUM(total) FILTER (WHERE metodo_pago IN ('yape', 'tarjeta')), 0) AS total_digital, " +
                "COUNT(*) AS cantidad_ventas " +
                "FROM ventas WHERE botica_id = :boticaId AND caja_id = :cajaId";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("cajaId", cajaId);
        Map<String, Object> fila = jdbc.queryForMap(sqlVentas, params);

        String sqlMovimientos = "SELECT COUNT(*) FROM movimientos_caja WHERE botica_id = :boticaId AND caja_id = :cajaId";
        Long cantidadMovimientos = jdbc.queryForObject(sqlMovimientos, params, Long.class);

        return new ResumenCierreResponse(
                (BigDecimal) fila.get("total_efectivo"),
                (BigDecimal) fila.get("total_digital"),
                ((Number) fila.get("cantidad_ventas")).longValue(),
                cantidadMovimientos == null ? 0L : cantidadMovimientos
        );
    }

    @Override
    public PaginaResponse<MovimientoCaja> listarMovimientosPaginado(Long boticaId, Long cajaId, int pagina, int tamano, String orden) {
        int paginaSegura = Math.max(pagina, 0);
        int tamanoSeguro = Math.min(Math.max(tamano, 1), 100);

        String campo = "creadoEn";
        String direccionTexto = "desc";
        if (orden != null && !orden.isBlank()) {
            String[] partes = orden.split(",", 2);
            campo = partes[0].trim();
            if (partes.length > 1) {
                direccionTexto = partes[1].trim();
            }
        }
        String columnaSql = COLUMNAS_ORDEN_MOVIMIENTOS.get(campo);
        if (columnaSql == null) {
            throw new OrdenInvalidoException(campo);
        }
        String direccion = "asc".equalsIgnoreCase(direccionTexto) ? "ASC" : "DESC";
        String orderBy = columnaSql + " " + direccion + ", id ASC";

        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("cajaId", cajaId)
                .addValue("limite", tamanoSeguro)
                .addValue("offset", paginaSegura * tamanoSeguro);

        String sqlDatos = "SELECT * FROM movimientos_caja WHERE botica_id = :boticaId AND caja_id = :cajaId " +
                "ORDER BY " + orderBy + " LIMIT :limite OFFSET :offset";
        List<MovimientoCaja> contenido = jdbc.query(sqlDatos, params, movimientoRowMapper);

        String sqlCount = "SELECT COUNT(*) FROM movimientos_caja WHERE botica_id = :boticaId AND caja_id = :cajaId";
        Long total = jdbc.queryForObject(sqlCount, params, Long.class);

        return PaginaResponse.de(contenido, paginaSegura, tamanoSeguro, total == null ? 0L : total);
    }
}
