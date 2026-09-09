package com.botica.backend.dao;

import com.botica.backend.dto.LoteResponse;
import com.botica.backend.exception.OrdenInvalidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 13, sección A (límites exactos del semáforo) + sección C
 * (integración SQL real). El CASE WHEN vive en LoteDaoJdbc (SQL), no en
 * Java -- por eso esto es un test de DAO contra Postgres real, no un
 * Mockito. @Transactional: cada test inserta sus propios lotes y hace
 * rollback automático al terminar, sin dejar rastro en botica_test.
 */
@SpringBootTest
@Transactional
class LoteDaoSemaforoTest {

    @Autowired
    private LoteDao loteDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long boticaId;
    private Long productoId;
    // hoy fijo y arbitrario -- el SQL recibe "hoy" como parámetro explícito (nunca CURRENT_DATE),
    // así que el resultado no depende de en qué fecha real corra la suite.
    private static final LocalDate HOY = LocalDate.of(2026, 1, 1);
    private static final int CRITICO_DIAS = 30;
    private static final int ADVERTENCIA_DIAS = 90;
    private static final int UMBRAL_STOCK_BAJO = 15;

    @BeforeEach
    void cargarProductoDelSeed() {
        boticaId = jdbcTemplate.queryForObject("SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'", Long.class);
        productoId = jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? LIMIT 1", Long.class, boticaId);
    }

    @Test
    void estadoVencimiento_enLosSieteLimitesExactos_VENCIDO_CRITICO_ADVERTENCIA_OK() {
        // -1/0 separa VENCIDO de CRITICO; 30/31 separa CRITICO de ADVERTENCIA; 90/91 separa ADVERTENCIA de OK.
        assertThat(estadoDeUnLoteQueVenceEn(-1)).isEqualTo("VENCIDO");
        assertThat(estadoDeUnLoteQueVenceEn(0)).isEqualTo("CRITICO");
        assertThat(estadoDeUnLoteQueVenceEn(1)).isEqualTo("CRITICO");
        assertThat(estadoDeUnLoteQueVenceEn(30)).isEqualTo("CRITICO");
        assertThat(estadoDeUnLoteQueVenceEn(31)).isEqualTo("ADVERTENCIA");
        assertThat(estadoDeUnLoteQueVenceEn(90)).isEqualTo("ADVERTENCIA");
        assertThat(estadoDeUnLoteQueVenceEn(91)).isEqualTo("OK");
    }

    @Test
    void stockEstado_enLosLimitesExactos_AGOTADO_CRITICO_OK() {
        assertThat(estadoDeStockConCantidad(0)).isEqualTo("AGOTADO");
        assertThat(estadoDeStockConCantidad(1)).isEqualTo("CRITICO");
        assertThat(estadoDeStockConCantidad(UMBRAL_STOCK_BAJO)).isEqualTo("CRITICO"); // 15, <= umbral
        assertThat(estadoDeStockConCantidad(UMBRAL_STOCK_BAJO + 1)).isEqualTo("OK"); // 16
    }

    @Test
    void listarPaginado_conColumnaDeOrdenNoPermitida_lanzaOrdenInvalido_sinTocarLaBaseDeDatos() {
        // "Orden malicioso" (Anexo D): cualquier texto que no esté en la lista blanca se rechaza
        // ANTES de armar el SQL -- nunca se concatena el parámetro del cliente directo.
        assertThatThrownBy(() -> loteDao.listarPaginado(
                boticaId, null, null, false, null, 0, 20,
                "id; DROP TABLE productos --", HOY, CRITICO_DIAS, ADVERTENCIA_DIAS, UMBRAL_STOCK_BAJO))
                .isInstanceOf(OrdenInvalidoException.class);

        // La tabla sigue intacta -- si el ataque hubiera pasado, esto ya no encontraría nada.
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM productos", Long.class);
        assertThat(total).isGreaterThan(0);
    }

    @Test
    void listarPaginado_totalElementosYTotalPaginas_coincidenConUnCountRealDeLaMismaConsulta() {
        // Anexo D: dos consultas (datos + COUNT) con los MISMOS filtros -- nunca traer todo y
        // cortar en Java. Producto propio (no uno del seed, que ya trae sus propios lotes) para
        // que "5 lotes insertados" sea también "5 lotes totales", sin matemática de delta.
        Long productoPropio = insertarProductoPropio();
        for (int i = 0; i < 5; i++) {
            insertarLoteDe(productoPropio, HOY.plusDays(10), 50);
        }

        var pagina1 = loteDao.listarPaginado(boticaId, null, null, false, productoPropio, 0, 2,
                null, HOY, CRITICO_DIAS, ADVERTENCIA_DIAS, UMBRAL_STOCK_BAJO);
        var pagina3 = loteDao.listarPaginado(boticaId, null, null, false, productoPropio, 2, 2,
                null, HOY, CRITICO_DIAS, ADVERTENCIA_DIAS, UMBRAL_STOCK_BAJO);

        assertThat(pagina1.totalElementos()).isEqualTo(5);
        assertThat(pagina1.totalPaginas()).isEqualTo(3); // ceil(5/2)
        assertThat(pagina1.contenido()).hasSize(2);
        assertThat(pagina3.contenido()).hasSize(1); // última página: el resto exacto, ni de más ni de menos
    }

    @Test
    void listarPaginado_conColumnaValida_siOrdenaYDevuelveContenido() {
        var pagina = loteDao.listarPaginado(
                boticaId, null, null, false, null, 0, 20,
                "stock,desc", HOY, CRITICO_DIAS, ADVERTENCIA_DIAS, UMBRAL_STOCK_BAJO);

        assertThat(pagina.contenido()).isNotEmpty();
    }

    private String estadoDeUnLoteQueVenceEn(int diasDesdeHoy) {
        Long loteId = insertarLote(HOY.plusDays(diasDesdeHoy), 100);
        Optional<LoteResponse> respuesta = loteDao.buscarResponsePorId(
                boticaId, loteId, HOY, CRITICO_DIAS, ADVERTENCIA_DIAS, UMBRAL_STOCK_BAJO);
        return respuesta.orElseThrow().estadoVencimiento();
    }

    private String estadoDeStockConCantidad(int stock) {
        Long loteId = insertarLote(HOY.plusYears(5), stock); // fecha lejana: que no interfiera con el semáforo de vencimiento
        Optional<LoteResponse> respuesta = loteDao.buscarResponsePorId(
                boticaId, loteId, HOY, CRITICO_DIAS, ADVERTENCIA_DIAS, UMBRAL_STOCK_BAJO);
        return respuesta.orElseThrow().stockEstado();
    }

    private Long insertarLote(LocalDate fechaVencimiento, int stock) {
        return insertarLoteDe(productoId, fechaVencimiento, stock);
    }

    private Long insertarLoteDe(Long deProductoId, LocalDate fechaVencimiento, int stock) {
        String codigo = "QA-" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                boticaId, deProductoId, codigo, fechaVencimiento, stock, new BigDecimal("1.00"));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM lotes WHERE botica_id = ? AND producto_id = ? AND codigo = ?",
                Long.class, boticaId, deProductoId, codigo);
    }

    private Long insertarProductoPropio() {
        String codigoBarras = "QA-" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre) " +
                        "VALUES (?, ?, ?, ?, ?)",
                boticaId, "Producto de prueba QA", "Otros", codigoBarras, "unidad");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? AND codigo_barras = ?",
                Long.class, boticaId, codigoBarras);
    }
}
