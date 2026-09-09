package com.botica.backend.dao;

import com.botica.backend.dto.LoteResponse;
import com.botica.backend.dto.PaginaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de aislamiento multi-botica (D1) para lotes — misma pregunta que
 * CajaDaoAislamientoTest: un id de lote que existe en la tabla pero es
 * de otra botica no debe aparecer nunca al consultar como la botica
 * incorrecta. Requiere botica_db real con el seed de desarrollo.
 */
@SpringBootTest
class LoteDaoAislamientoTest {

    @Autowired
    private LoteDao loteDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long boticaSanLucasId;
    private Long boticaVidaSanaId;
    private Long productoDeVidaSanaId;

    @BeforeEach
    void cargarIdsDelSeed() {
        boticaSanLucasId = jdbcTemplate.queryForObject(
                "SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'", Long.class);
        boticaVidaSanaId = jdbcTemplate.queryForObject(
                "SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'", Long.class);
        productoDeVidaSanaId = jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? LIMIT 1", Long.class, boticaVidaSanaId);

        assertThat(boticaSanLucasId).as("seed debe existir").isNotNull();
        assertThat(boticaVidaSanaId).isNotNull();
        assertThat(productoDeVidaSanaId).as("Vida Sana debe tener al menos un producto en el seed").isNotNull();
    }

    @Test
    void listarPaginado_filtrandoPorProductoDeOtraBotica_devuelveVacio() {
        PaginaResponse<LoteResponse> pagina = loteDao.listarPaginado(
                boticaSanLucasId, null, "todos", false, productoDeVidaSanaId,
                0, 20, null, LocalDate.now(), 30, 90, 15);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
    }

    @Test
    void mismaConsulta_conLaBoticaCorrecta_siEncuentraLotes() {
        PaginaResponse<LoteResponse> pagina = loteDao.listarPaginado(
                boticaVidaSanaId, null, "todos", false, productoDeVidaSanaId,
                0, 20, null, LocalDate.now(), 30, 90, 15);

        assertThat(pagina.contenido()).isNotEmpty();
    }

    @Test
    void existeProducto_conProductoDeOtraBotica_devuelveFalse() {
        boolean existe = loteDao.existeProducto(boticaSanLucasId, productoDeVidaSanaId);

        assertThat(existe).as("un producto de Vida Sana no debe 'existir' al validar como San Lucas").isFalse();
    }
}
