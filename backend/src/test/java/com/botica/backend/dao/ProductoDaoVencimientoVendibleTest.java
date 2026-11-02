package com.botica.backend.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DIF-13 (Tarea 0B, revisa D6): el vencimiento que ve el cajero en Punto de
 * Venta es el del primer lote vendible (stock > 0 y no vencido), el mismo que
 * va a consumir FEFO. Antes un lote vencido con stock "teñía" el producto
 * aunque la venta tomara otro. hoy es fijo. @Transactional: rollback automático.
 */
@SpringBootTest
@Transactional
class ProductoDaoVencimientoVendibleTest {

    private static final LocalDate HOY = LocalDate.of(2026, 1, 1);

    @Autowired
    private ProductoDao productoDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long boticaId;

    @BeforeEach
    void cargarBoticaDelSeed() {
        boticaId = jdbcTemplate.queryForObject("SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'", Long.class);
    }

    @Test
    void conLoteVencidoYLoteVigente_devuelveLaFechaDelVigente() {
        long productoId = insertarProducto();
        insertarLote(productoId, HOY.minusDays(3), 50);
        insertarLote(productoId, HOY.plusMonths(8), 20);

        Map<Long, LocalDate> fechas = productoDao.fechaVencimientoMasProximaPorProductos(List.of(productoId), HOY);

        assertThat(fechas.get(productoId)).as("FEFO vende el vigente: eso es lo que debe ver el cajero").isEqualTo(HOY.plusMonths(8));
    }

    @Test
    void conTodoSuStockVencido_noDevuelveFecha() {
        long productoId = insertarProducto();
        insertarLote(productoId, HOY.minusDays(3), 50);

        assertThat(productoDao.fechaVencimientoMasProximaPorProductos(List.of(productoId), HOY)).doesNotContainKey(productoId);
    }

    @Test
    void conLoteQueVenceHoy_devuelveHoy() {
        long productoId = insertarProducto();
        insertarLote(productoId, HOY, 5);

        assertThat(productoDao.fechaVencimientoMasProximaPorProductos(List.of(productoId), HOY).get(productoId)).isEqualTo(HOY);
    }

    private long insertarProducto() {
        String codigoBarras = "QA-" + (System.nanoTime() % 1_000_000_000_000L); // VARCHAR(20), BE-010
        jdbcTemplate.update(
                "INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre) VALUES (?, ?, ?, ?, ?)",
                boticaId, "QA vencimiento vendible", "Otros", codigoBarras, "unidad");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? AND codigo_barras = ?", Long.class, boticaId, codigoBarras);
    }

    private void insertarLote(long productoId, LocalDate fechaVencimiento, int stock) {
        jdbcTemplate.update(
                "INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) VALUES (?, ?, ?, ?, ?, ?)",
                boticaId, productoId, "QA-VV-" + System.nanoTime(), fechaVencimiento, stock, new BigDecimal("1.00"));
    }
}
