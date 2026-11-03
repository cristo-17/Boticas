package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.AlertaDao;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.AlertaResponse;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Alerta "caja sin cerrar" con el turno Noche (DECISIONES 2026-11-03). La caja
 * de Noche se abre a las 22:00 del día D (fecha = D) y se cierra a las 07:00 de
 * D+1: en esa madrugada no está "sin cerrar". La regla cuenta un día más para la
 * Noche (fecha + 1 < hoy), así que salta desde las 00:00 de D+2. Las de Mañana
 * y Tarde no cambian (fecha < hoy).
 *
 * Cadena real: AlertaService.listar con FechaNegocio de reloj fijo (la única
 * fuente de "hoy", Regla 5), AlertaDao real y la base de test. @Transactional:
 * rollback automático.
 */
@SpringBootTest
@Transactional
class AlertaCajaNocheTest {

    private static final LocalDate D = LocalDate.of(2026, 1, 10);

    @Autowired
    private AlertaDao alertaDao;
    @Autowired
    private CajaDao cajaDao;
    @Autowired
    private ConfigNegocioProperties config;
    @Autowired
    private JdbcTemplate jdbc;

    private Long boticaId;
    private Long usuarioId;

    @BeforeEach
    void usuarioPropio() {
        boticaId = jdbc.queryForObject("SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'", Long.class);
        // Usuario nuevo: el índice de una sola caja abierta por usuario (V8) no choca con el seed.
        usuarioId = jdbc.queryForObject(
                "INSERT INTO usuarios (botica_id, rol_id, nombre, usuario, password_hash) "
                        + "VALUES (?, (SELECT id FROM roles WHERE nombre = 'TECNICO'), 'Técnico Noche QA', 'qa.noche', 'x') RETURNING id",
                Long.class, boticaId);
    }

    private long abrirCaja(String turno, LocalDate fecha, int hora) {
        return jdbc.queryForObject(
                "INSERT INTO caja_diaria (botica_id, usuario_id, fecha, turno, monto_apertura, hora_apertura) "
                        + "VALUES (?, ?, ?, ?, 100.00, ?) RETURNING id",
                Long.class, boticaId, usuarioId, fecha, turno,
                ZonedDateTime.of(fecha, LocalTime.of(hora, 0), FechaNegocio.ZONA_LIMA).toOffsetDateTime());
    }

    /** Las alertas que vería la botica con el reloj de Lima en ese instante. */
    private boolean saltaLaAlerta(long cajaId, LocalDate dia, LocalTime hora) {
        Clock reloj = Clock.fixed(ZonedDateTime.of(dia, hora, FechaNegocio.ZONA_LIMA).toInstant(), FechaNegocio.ZONA_LIMA);
        ContextoOperacion contexto = mock(ContextoOperacion.class);
        when(contexto.boticaId()).thenReturn(boticaId);
        AlertaService servicio = new AlertaService(alertaDao, cajaDao, contexto, config, new FechaNegocio(reloj));
        return servicio.listar(0, 100).contenido().stream()
                .map(AlertaResponse::id)
                .anyMatch(id -> id.equals("caja-" + cajaId));
    }

    @Test
    void cajaDeNocheAbierta_aLas0300DeLaMadrugadaSiguiente_noSalta() {
        long caja = abrirCaja("Noche", D, 22);
        assertThat(saltaLaAlerta(caja, D.plusDays(1), LocalTime.of(3, 0)))
                .as("a las 03:00 de D+1 la Noche del día D sigue en su turno")
                .isFalse();
    }

    @Test
    void cajaDeNocheAbierta_alFinalDelDiaSiguiente_todaviaNoSalta() {
        long caja = abrirCaja("Noche", D, 22);
        assertThat(saltaLaAlerta(caja, D.plusDays(1), LocalTime.of(23, 59))).isFalse();
    }

    @Test
    void cajaDeNocheAbierta_elDiaDespuesDeLaNoche_siSalta() {
        long caja = abrirCaja("Noche", D, 22);
        assertThat(saltaLaAlerta(caja, D.plusDays(2), LocalTime.of(0, 1)))
                .as("desde las 00:00 de D+2 la Noche del día D quedó sin cerrar")
                .isTrue();
        assertThat(saltaLaAlerta(caja, D.plusDays(2), LocalTime.of(3, 0))).isTrue();
    }

    @Test
    void cajaDeManana_sinCambios_saltaALas0300DelDiaSiguiente() {
        long caja = abrirCaja("Mañana", D, 7);
        assertThat(saltaLaAlerta(caja, D, LocalTime.of(23, 0))).isFalse();
        assertThat(saltaLaAlerta(caja, D.plusDays(1), LocalTime.of(3, 0))).isTrue();
    }
}
