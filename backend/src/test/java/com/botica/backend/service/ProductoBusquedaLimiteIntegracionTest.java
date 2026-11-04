package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dto.ProductoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RF-08: la búsqueda de Punto de Venta devuelve como máximo 20 resultados, por nombre, también por laboratorio. */
@SpringBootTest
@Transactional
class ProductoBusquedaLimiteIntegracionTest {

    @Autowired
    private ProductoService productoService;
    @Autowired
    private ContextoOperacion contexto;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void con25Coincidencias_devuelveLas20PrimerasPorNombre() {
        for (int i = 25; i >= 1; i--) producto(String.format("Zlimite QA %02d", i), "Lab Uno");

        List<ProductoResponse> resultados = productoService.buscar("zlimite qa", false);

        assertThat(resultados).hasSize(20);
        assertThat(resultados.get(0).nombre()).isEqualTo("Zlimite QA 01");
        assertThat(resultados.get(19).nombre()).isEqualTo("Zlimite QA 20");
    }

    @Test
    void porLaboratorio_tambienCortaEn20() {
        for (int i = 1; i <= 22; i++) producto("Producto lab QA " + i, "Laboratorio Zlimite");
        assertThat(productoService.buscar("laboratorio zlimite", false)).hasSize(20);
    }

    private void producto(String nombre, String laboratorio) {
        Long boticaId = contexto.boticaId();
        String codigo = "QA-LI-" + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        Long id = jdbc.queryForObject("INSERT INTO productos (botica_id, nombre, laboratorio, categoria, codigo_barras, unidad_nombre, requiere_receta) " +
                "VALUES (?, ?, ?, 'Otros', ?, 'unidad', false) RETURNING id", Long.class, boticaId, nombre, laboratorio, codigo);
        jdbc.update("INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) VALUES (?, ?, 'Unidad', 1, ?)",
                boticaId, id, new BigDecimal("1.00"));
    }
}
