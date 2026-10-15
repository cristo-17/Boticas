package com.botica.backend.dao;

import com.botica.backend.model.Notificacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T15 V3: mismo criterio que CajaDaoAislamientoTest. Si alguien de San
 * Lucas consigue el id de una notificación de Vida Sana, ninguna consulta
 * se la muestra ni le deja tocarla. Y dentro de la botica, el rol del
 * token decide qué ve y qué marca (una de ADMINISTRADOR no la toca un TECNICO).
 * @Transactional: cada test deja botica_test como estaba.
 */
@SpringBootTest
@Transactional
class NotificacionDaoAislamientoTest {

    @Autowired
    private NotificacionDao notificacionDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long sanLucas;
    private Long vidaSana;

    @BeforeEach
    void cargarIdsDelSeed() {
        sanLucas = jdbcTemplate.queryForObject("SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'", Long.class);
        vidaSana = jdbcTemplate.queryForObject("SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'", Long.class);
    }

    @Test
    void listarRecientes_nuncaDevuelveNotificacionesDeOtraBotica() {
        Notificacion ajena = insertar(vidaSana, null);

        List<Notificacion> deSanLucas = notificacionDao.listarRecientes(sanLucas, "ADMINISTRADOR", 100);

        assertThat(deSanLucas).extracting(Notificacion::getId).doesNotContain(ajena.getId());
        assertThat(notificacionDao.listarRecientes(vidaSana, "ADMINISTRADOR", 100))
                .extracting(Notificacion::getId).contains(ajena.getId());
    }

    @Test
    void marcarComoLeida_conIdDeOtraBotica_noLaToca_yDevuelveCero() {
        Notificacion ajena = insertar(vidaSana, null);

        int filas = notificacionDao.marcarComoLeida(sanLucas, "ADMINISTRADOR", ajena.getId());

        assertThat(filas).isZero();
        assertThat(leida(ajena.getId())).isFalse();
    }

    @Test
    void marcarComoLeida_tecnicoConNotificacionSoloDeAdministrador_noLaToca() {
        Notificacion deAdmin = insertar(sanLucas, "ADMINISTRADOR");

        int filas = notificacionDao.marcarComoLeida(sanLucas, "TECNICO", deAdmin.getId());

        assertThat(filas).isZero();
        assertThat(leida(deAdmin.getId())).isFalse();
    }

    @Test
    void marcarComoLeida_mismaBoticaYRolCorrecto_siLaMarca() {
        Notificacion propia = insertar(sanLucas, "ADMINISTRADOR");

        assertThat(notificacionDao.marcarComoLeida(sanLucas, "ADMINISTRADOR", propia.getId())).isEqualTo(1);
        assertThat(leida(propia.getId())).isTrue();
    }

    @Test
    void marcarTodasComoLeidas_noTocaLasDeOtraBotica() {
        Notificacion ajena = insertar(vidaSana, null);

        notificacionDao.marcarTodasComoLeidas(sanLucas, "ADMINISTRADOR");

        assertThat(leida(ajena.getId())).isFalse();
    }

    @Test
    void contarNoLeidas_soloCuentaLasDeSuBotica() {
        long antes = notificacionDao.contarNoLeidas(sanLucas, "ADMINISTRADOR");
        insertar(vidaSana, null);

        assertThat(notificacionDao.contarNoLeidas(sanLucas, "ADMINISTRADOR")).isEqualTo(antes);
    }

    private Notificacion insertar(Long boticaId, String rolDestinatario) {
        return notificacionDao.insertar(Notificacion.builder()
                .boticaId(boticaId).rolDestinatario(rolDestinatario)
                .tipo("STOCK_CRITICO").titulo("QA aislamiento").mensaje("QA").leido(false)
                .build());
    }

    private boolean leida(Long id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT leido FROM notificaciones WHERE id = ?", Boolean.class, id));
    }
}
