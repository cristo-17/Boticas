package com.botica.backend.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 13, sección H (D2): mermas.valor_venta nunca se confunde con el
 * costo. precio y costo del MISMO lote se fijan deliberadamente muy
 * distintos (50.00 vs 5.00) -- si bloquearLoteConPrecio alguna vez
 * leyera costo_unitario en vez de presentaciones.precio, este test
 * fallaría con un número visiblemente incorrecto (5.00), no uno que por
 * casualidad coincide. @Transactional: rollback automático.
 */
@SpringBootTest
@Transactional
class MermaDaoPrecioVsCostoTest {

    @Autowired
    private MermaDao mermaDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long boticaId;

    @BeforeEach
    void cargarBoticaDelSeed() {
        boticaId = jdbcTemplate.queryForObject("SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'", Long.class);
    }

    @Test
    void bloquearLoteConPrecio_devuelveElPrecioDeVenta_nuncaElCostoDelLote_aunqueSeanMuyDistintos() {
        String codigoBarras = "QA-" + (System.nanoTime() % 1_000_000_000_000L);
        jdbcTemplate.update(
                "INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre) VALUES (?, ?, ?, ?, ?)",
                boticaId, "Producto precio vs costo QA", "Otros", codigoBarras, "unidad");
        Long productoId = jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? AND codigo_barras = ?", Long.class, boticaId, codigoBarras);

        jdbcTemplate.update(
                "INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) VALUES (?, ?, 'Unidad', 1, 50.00)",
                boticaId, productoId);

        String codigoLote = "QA-L-" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                        "VALUES (?, ?, ?, CURRENT_DATE + INTERVAL '1 year', 10, 5.00)",
                boticaId, productoId, codigoLote);
        Long loteId = jdbcTemplate.queryForObject(
                "SELECT id FROM lotes WHERE producto_id = ? AND codigo = ?", Long.class, productoId, codigoLote);

        Optional<MermaDao.LoteParaMerma> resultado = mermaDao.bloquearLoteConPrecio(boticaId, loteId);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().precioUnitario())
                .as("50.00 = presentaciones.precio; si esto diera 5.00, estaría leyendo lotes.costo_unitario por error")
                .isEqualByComparingTo("50.00");
    }
}
