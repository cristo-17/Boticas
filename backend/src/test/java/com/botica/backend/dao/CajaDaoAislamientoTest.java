package com.botica.backend.dao;

import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.model.MovimientoCaja;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de aislamiento multi-botica (D1), corriendo desde la Tarea 8 y no
 * esperando a la Tarea 13 — es la primera vez que hay SQL real de un
 * módulo completo donde probarlo. Requiere botica_db real con el seed
 * de la Tarea 7 (dos boticas con datos distintos) — mismas variables de
 * entorno DB_USERNAME/DB_PASSWORD que BackendApplicationTests.
 *
 * La pregunta que responde: si alguien autenticado en la botica A
 * consigue (adivina, filtra de un log, etc.) el id numérico de una caja
 * de la botica B, ¿el DAO se la devuelve igual porque el id "existe" en
 * la tabla? Debe responder que NO en todos los casos.
 */
@SpringBootTest
class CajaDaoAislamientoTest {

    @Autowired
    private CajaDao cajaDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long boticaSanLucasId;
    private Long boticaVidaSanaId;
    private Long cajaDeVidaSanaId;

    @BeforeEach
    void cargarIdsDelSeed() {
        boticaSanLucasId = jdbcTemplate.queryForObject(
                "SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'", Long.class);
        boticaVidaSanaId = jdbcTemplate.queryForObject(
                "SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'", Long.class);
        cajaDeVidaSanaId = jdbcTemplate.queryForObject(
                "SELECT id FROM caja_diaria WHERE botica_id = ? LIMIT 1", Long.class, boticaVidaSanaId);

        assertThat(boticaSanLucasId).as("seed de la Tarea 7 debe existir").isNotNull();
        assertThat(boticaVidaSanaId).isNotNull();
        assertThat(cajaDeVidaSanaId).as("Botica Vida Sana debe tener al menos una caja en el seed").isNotNull();
    }

    @Test
    void buscarPorId_conCajaDeOtraBotica_noLaEncuentra() {
        // La caja EXISTE en la tabla, solo que pertenece a otra botica.
        Optional<CajaDiaria> resultado = cajaDao.buscarPorId(boticaSanLucasId, cajaDeVidaSanaId);

        assertThat(resultado).as("una caja de Vida Sana no debe aparecer al consultar como San Lucas").isEmpty();
    }

    @Test
    void calcularResumen_conCajaDeOtraBotica_daCerosEnVezDeDatosAjenos() {
        // calcularResumen no valida existencia (eso lo hace el Service antes de llamarlo);
        // el propio SQL con WHERE botica_id = ? debe devolver cero filas, nunca las de Vida Sana.
        var resumen = cajaDao.calcularResumen(boticaSanLucasId, cajaDeVidaSanaId);

        assertThat(resumen.cantidadVentas()).isZero();
        assertThat(resumen.cantidadMovimientos()).isZero();
    }

    @Test
    void listarMovimientosPaginado_conCajaDeOtraBotica_devuelveVacio() {
        PaginaResponse<MovimientoCaja> pagina =
                cajaDao.listarMovimientosPaginado(boticaSanLucasId, cajaDeVidaSanaId, 0, 20, null);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
    }

    @Test
    void mismaConsulta_conLaBoticaCorrecta_siEncuentraLaCaja() {
        // Control positivo: el aislamiento no es "nunca encuentra nada", es "encuentra
        // exactamente lo de su propia botica".
        Optional<CajaDiaria> resultado = cajaDao.buscarPorId(boticaVidaSanaId, cajaDeVidaSanaId);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getBoticaId()).isEqualTo(boticaVidaSanaId);
    }
}
