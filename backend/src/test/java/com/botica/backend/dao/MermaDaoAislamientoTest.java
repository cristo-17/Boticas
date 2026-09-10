package com.botica.backend.dao;

import com.botica.backend.dto.MermaResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.model.Merma;
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

/**
 * Tarea 13, sección G (aislamiento multi-botica pendiente): misma
 * pregunta que LoteDaoAislamientoTest/CajaDaoAislamientoTest, aplicada
 * a Merma. La merma del seed ("de ayer") tiene una fecha que se mueve
 * con el día real -- por eso acá insertamos una merma propia con fecha
 * de HOY (default now() de la columna) y la consultamos por esa fecha
 * fija, en vez de depender de la del seed. @Transactional: rollback
 * automático, no deja filas en botica_test.
 */
@SpringBootTest
@Transactional
class MermaDaoAislamientoTest {

    @Autowired
    private MermaDao mermaDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long boticaSanLucasId;
    private Long boticaVidaSanaId;
    private Long tecnicoDeVidaSanaId;
    private Long loteDeVidaSanaId;

    @BeforeEach
    void cargarIdsDelSeed() {
        boticaSanLucasId = jdbcTemplate.queryForObject(
                "SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'", Long.class);
        boticaVidaSanaId = jdbcTemplate.queryForObject(
                "SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'", Long.class);
        tecnicoDeVidaSanaId = jdbcTemplate.queryForObject(
                "SELECT id FROM usuarios WHERE botica_id = ? LIMIT 1", Long.class, boticaVidaSanaId);
        loteDeVidaSanaId = jdbcTemplate.queryForObject(
                "SELECT id FROM lotes WHERE botica_id = ? AND stock > 0 LIMIT 1", Long.class, boticaVidaSanaId);

        assertThat(boticaSanLucasId).as("seed debe existir").isNotNull();
        assertThat(boticaVidaSanaId).isNotNull();
        assertThat(loteDeVidaSanaId).as("Vida Sana debe tener al menos un lote con stock en el seed").isNotNull();
    }

    @Test
    void listarPaginado_deHoy_paraSanLucas_noVeLaMermaDeVidaSana() {
        mermaDao.insertar(Merma.builder()
                .boticaId(boticaVidaSanaId).loteId(loteDeVidaSanaId).usuarioId(tecnicoDeVidaSanaId)
                .cantidad(1).motivo("Vencimiento").valorVenta(new BigDecimal("5.00"))
                .build());

        PaginaResponse<MermaResponse> comoSanLucas = mermaDao.listarPaginado(
                boticaSanLucasId, LocalDate.now(), 0, 20, null);

        assertThat(comoSanLucas.contenido())
                .as("la merma recién insertada es de Vida Sana -- San Lucas no debe verla")
                .isEmpty();
        assertThat(comoSanLucas.totalElementos()).isZero();
    }

    @Test
    void mismaConsulta_conLaBoticaCorrecta_siEncuentraLaMerma() {
        Merma insertada = mermaDao.insertar(Merma.builder()
                .boticaId(boticaVidaSanaId).loteId(loteDeVidaSanaId).usuarioId(tecnicoDeVidaSanaId)
                .cantidad(1).motivo("Vencimiento").valorVenta(new BigDecimal("5.00"))
                .build());

        PaginaResponse<MermaResponse> comoVidaSana = mermaDao.listarPaginado(
                boticaVidaSanaId, LocalDate.now(), 0, 20, null);

        assertThat(comoVidaSana.contenido())
                .extracting(MermaResponse::id)
                .contains(insertada.getId());
    }

    @Test
    void bloquearLoteConPrecio_conLoteDeOtraBotica_noLoEncuentra() {
        Optional<MermaDao.LoteParaMerma> resultado =
                mermaDao.bloquearLoteConPrecio(boticaSanLucasId, loteDeVidaSanaId);

        assertThat(resultado)
                .as("un lote de Vida Sana no debe poder bloquearse para una merma como San Lucas")
                .isEmpty();
    }
}
