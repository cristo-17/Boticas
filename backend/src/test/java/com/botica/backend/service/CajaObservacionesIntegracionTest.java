package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.CajaResponse;
import com.botica.backend.dto.CerrarCajaRequest;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** RF-36: las observaciones del cierre se guardan completas hasta 500 caracteres (también con tildes y eñes). */
@SpringBootTest
@Transactional
class CajaObservacionesIntegracionTest {

    @Autowired
    private CajaService cajaService;
    @Autowired
    private CajaDao cajaDao;
    @Autowired
    private ContextoOperacion contexto;
    @Autowired
    private FechaNegocio fechaNegocio;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void cerrar_conObservacionesDe500Caracteres_lasGuardaYLasDevuelveCompletas() {
        Long cajaId = cajaAbierta();
        String observaciones = "Señor Ñandú pagó con billete roto. ".repeat(15).substring(0, 500);
        assertThat(observaciones).hasSize(500);

        CajaResponse cerrada = cajaService.cerrar(new CerrarCajaRequest(new BigDecimal("100.00"), observaciones, null));

        assertThat(cerrada.observaciones()).isEqualTo(observaciones);
        assertThat(jdbc.queryForObject("SELECT observaciones FROM caja_diaria WHERE id = ?", String.class, cajaId))
                .isEqualTo(observaciones);
    }

    @Test
    void cerrar_sinObservaciones_guardaNull() {
        Long cajaId = cajaAbierta();
        cajaService.cerrar(new CerrarCajaRequest(new BigDecimal("100.00"), null, null));
        assertThat(jdbc.queryForObject("SELECT observaciones FROM caja_diaria WHERE id = ?", String.class, cajaId)).isNull();
    }

    private Long cajaAbierta() {
        Long boticaId = contexto.boticaId();
        return cajaDao.buscarAbiertaDelUsuario(boticaId, contexto.usuarioId())
                .orElseGet(() -> cajaDao.insertar(CajaDiaria.builder()
                        .boticaId(boticaId).usuarioId(contexto.usuarioId()).fecha(fechaNegocio.hoy()).turno(contexto.turno())
                        .montoApertura(new BigDecimal("100.00")).horaApertura(fechaNegocio.ahora())
                        .build()))
                .getId();
    }
}
