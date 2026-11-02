package com.botica.backend.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DIF-11 (Tarea 0B): stock crítico, agotado y la fila de stock bajo de
 * Alertas cuentan PRODUCTOS por su stock vendible (la suma de sus lotes no
 * vencidos), no lotes sueltos. Antes, un lote viejo en 0 hacía "agotado"
 * a un producto con otro lote lleno, y un producto con todo su stock
 * vencido no salía agotado.
 *
 * Cada test compara el conteo antes y después de insertar su producto, así
 * no depende de cuántos productos trae el seed. hoy es fijo, igual que en
 * AlertaDaoAislamientoTest. @Transactional: rollback automático.
 */
@SpringBootTest
@Transactional
class AlertaDaoStockVendibleTest {

    private static final LocalDate HOY = LocalDate.of(2026, 1, 1);
    private static final int UMBRAL = 15;

    @Autowired
    private AlertaDao alertaDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long boticaId;

    @BeforeEach
    void cargarBoticaDelSeed() {
        boticaId = jdbcTemplate.queryForObject("SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'", Long.class);
    }

    @Test
    void agotado_productoConUnLoteViejoEn0YOtroCon100_noSaleAgotadoNiEnStockBajo() {
        long antes = alertaDao.contarProductosStockAgotado(boticaId, HOY);
        String nombre = "QA vendible " + System.nanoTime();
        long productoId = insertarProducto(nombre);
        insertarLote(productoId, HOY.plusMonths(6), 0);
        insertarLote(productoId, HOY.plusYears(1), 100);

        assertThat(alertaDao.contarProductosStockAgotado(boticaId, HOY))
                .as("tiene 100 unidades vendibles: el lote viejo en 0 no lo hace agotado")
                .isEqualTo(antes);
        assertThat(alertaDao.nombresProductosStockBajo(boticaId, UMBRAL, HOY, 1000)).doesNotContain(nombre);
    }

    @Test
    void agotado_productoConTodoSuStockVencido_saleAgotadoYEnStockBajo() {
        long antes = alertaDao.contarProductosStockAgotado(boticaId, HOY);
        String nombre = "QA vendible " + System.nanoTime();
        long productoId = insertarProducto(nombre);
        insertarLote(productoId, HOY.minusDays(1), 50);

        assertThat(alertaDao.contarProductosStockAgotado(boticaId, HOY))
                .as("50 unidades, todas vencidas: no hay nada que vender")
                .isEqualTo(antes + 1);
        assertThat(alertaDao.nombresProductosStockBajo(boticaId, UMBRAL, HOY, 1000)).contains(nombre);
    }

    @Test
    void stockCritico_productoConDosLotesDe10_sumaVeinte_noEsCritico() {
        long antes = alertaDao.contarProductosStockCritico(boticaId, UMBRAL, HOY);
        long productoId = insertarProducto("QA vendible " + System.nanoTime());
        insertarLote(productoId, HOY.plusMonths(6), 10);
        insertarLote(productoId, HOY.plusYears(1), 10);

        assertThat(alertaDao.contarProductosStockCritico(boticaId, UMBRAL, HOY))
                .as("10 + 10 = 20 vendibles, sobre el umbral de 15")
                .isEqualTo(antes);
    }

    @Test
    void stockCritico_productoConVencido100YVigente10_esCritico() {
        long antes = alertaDao.contarProductosStockCritico(boticaId, UMBRAL, HOY);
        long productoId = insertarProducto("QA vendible " + System.nanoTime());
        insertarLote(productoId, HOY.minusDays(1), 100);
        insertarLote(productoId, HOY.plusYears(1), 10);

        assertThat(alertaDao.contarProductosStockCritico(boticaId, UMBRAL, HOY))
                .as("solo 10 vendibles: las 100 vencidas no cuentan")
                .isEqualTo(antes + 1);
    }

    private long insertarProducto(String nombre) {
        // VARCHAR(20): "QA-" + hasta 12 dígitos (BE-010).
        String codigoBarras = "QA-" + (System.nanoTime() % 1_000_000_000_000L);
        jdbcTemplate.update(
                "INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre) VALUES (?, ?, ?, ?, ?)",
                boticaId, nombre, "Otros", codigoBarras, "unidad");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? AND codigo_barras = ?", Long.class, boticaId, codigoBarras);
    }

    private void insertarLote(long productoId, LocalDate fechaVencimiento, int stock) {
        jdbcTemplate.update(
                "INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) VALUES (?, ?, ?, ?, ?, ?)",
                boticaId, productoId, "QA-VEND-" + System.nanoTime(), fechaVencimiento, stock, new BigDecimal("1.00"));
    }
}
