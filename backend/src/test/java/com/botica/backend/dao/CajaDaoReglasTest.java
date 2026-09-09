package com.botica.backend.dao;

import com.botica.backend.model.CajaDiaria;
import com.botica.backend.model.MovimientoCaja;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 13, sección C: el índice único parcial
 * (`idx_caja_abierta_unica`, V1__esquema.sql) es la última línea de
 * defensa contra dos cajas abiertas del mismo usuario/turno/día -- el
 * Service ya lo valida (CajaServiceTest), esto prueba que la base
 * también lo hace, a nivel SQL, aunque alguien se salte el Service.
 * @Transactional: rollback automático, no deja filas en botica_test.
 */
@SpringBootTest
@Transactional
class CajaDaoReglasTest {

    @Autowired
    private CajaDao cajaDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long boticaId;
    private Long usuarioId;

    @BeforeEach
    void cargarUsuarioDelSeed() {
        boticaId = jdbcTemplate.queryForObject("SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'", Long.class);
        usuarioId = jdbcTemplate.queryForObject(
                "SELECT id FROM usuarios WHERE botica_id = ? LIMIT 1", Long.class, boticaId);
    }

    @Test
    void indiceUnicoDeCajaAbierta_rechazaUnSegundoInsertDirectoPorSql_mismoUsuarioTurnoYDia() {
        LocalDate hoy = LocalDate.of(2026, 6, 15);
        CajaDiaria primera = CajaDiaria.builder()
                .boticaId(boticaId).usuarioId(usuarioId).fecha(hoy).turno("Tarde")
                .montoApertura(new BigDecimal("100.00")).horaApertura(OffsetDateTime.now(FechaNegocio.ZONA_LIMA))
                .build();
        cajaDao.insertar(primera); // vía el DAO, como en producción -- deja estado ABIERTA por default

        CajaDiaria segunda = CajaDiaria.builder()
                .boticaId(boticaId).usuarioId(usuarioId).fecha(hoy).turno("Tarde")
                .montoApertura(new BigDecimal("50.00")).horaApertura(OffsetDateTime.now(FechaNegocio.ZONA_LIMA))
                .build();

        // Este es el INSERT crudo que la base rechaza -- no pasa por CajaService (que ya lo
        // bloquea antes con existeCajaAbierta): esto prueba la RED DE SEGURIDAD del índice
        // en sí, no la validación de la capa de arriba.
        assertThatThrownBy(() -> cajaDao.insertar(segunda))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void indiceUnicoDeCajaAbierta_permiteDosAbiertasDelMismoUsuarioSiUnaYaEstaCerrada() {
        // Control positivo: el índice es parcial (WHERE estado = 'ABIERTA'), no total -- una
        // caja CERRADA no cuenta para el conflicto. Sin esto, nadie podría volver a abrir caja
        // al día siguiente con el mismo turno.
        LocalDate hoy = LocalDate.of(2026, 6, 15);
        CajaDiaria primera = cajaDao.insertar(CajaDiaria.builder()
                .boticaId(boticaId).usuarioId(usuarioId).fecha(hoy).turno("Mañana")
                .montoApertura(new BigDecimal("100.00")).horaApertura(OffsetDateTime.now(FechaNegocio.ZONA_LIMA))
                .build());
        primera.setMontoContado(new BigDecimal("100.00"));
        primera.setMontoEsperado(new BigDecimal("100.00"));
        primera.setDiferencia(BigDecimal.ZERO);
        primera.setSemaforoDescuadre("EXACTO");
        primera.setHoraCierre(OffsetDateTime.now(FechaNegocio.ZONA_LIMA));
        cajaDao.actualizarCierre(primera); // pasa a CERRADA

        CajaDiaria segunda = CajaDiaria.builder()
                .boticaId(boticaId).usuarioId(usuarioId).fecha(hoy).turno("Mañana")
                .montoApertura(new BigDecimal("120.00")).horaApertura(OffsetDateTime.now(FechaNegocio.ZONA_LIMA))
                .build();

        assertThat(cajaDao.insertar(segunda).getId()).isNotEqualTo(primera.getId()); // no lanza, inserta una nueva
    }

    @Test
    void indiceUnicoDeCajaAbierta_noBloqueaEntreBoticas_mismoTurnoYFecha() {
        // Sección G (D1): el índice incluye botica_id -- dos boticas pueden tener cada una su
        // propia caja abierta el mismo turno/día sin pisarse, aunque sean usuarios distintos.
        Long boticaVidaSanaId = jdbcTemplate.queryForObject(
                "SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'", Long.class);
        Long usuarioVidaSanaId = jdbcTemplate.queryForObject(
                "SELECT id FROM usuarios WHERE botica_id = ? LIMIT 1", Long.class, boticaVidaSanaId);
        LocalDate hoy = LocalDate.of(2026, 6, 15);

        CajaDiaria sanLucas = cajaDao.insertar(CajaDiaria.builder()
                .boticaId(boticaId).usuarioId(usuarioId).fecha(hoy).turno("Tarde")
                .montoApertura(new BigDecimal("100.00")).horaApertura(OffsetDateTime.now(FechaNegocio.ZONA_LIMA))
                .build());
        CajaDiaria vidaSana = cajaDao.insertar(CajaDiaria.builder()
                .boticaId(boticaVidaSanaId).usuarioId(usuarioVidaSanaId).fecha(hoy).turno("Tarde")
                .montoApertura(new BigDecimal("100.00")).horaApertura(OffsetDateTime.now(FechaNegocio.ZONA_LIMA))
                .build());

        assertThat(sanLucas.getId()).isNotEqualTo(vidaSana.getId()); // ninguna de las dos lanza DataIntegrityViolationException
    }

    @Test
    void sumarMovimientosEfectivo_esVentasEnEfectivoMenosEgresos_ignorandoLoQueNoAfectaEfectivo() {
        // El invariante de CajaService.cerrar() (Tarea 13, sección B): monto_esperado =
        // monto_apertura + sumarMovimientosEfectivo. Acá se prueba la mitad SQL real de esa
        // suma -- CajaServiceTest ya prueba la otra mitad (el Service) con el DAO mockeado.
        CajaDiaria caja = cajaDao.insertar(CajaDiaria.builder()
                .boticaId(boticaId).usuarioId(usuarioId).fecha(LocalDate.of(2026, 6, 20)).turno("Noche")
                .montoApertura(new BigDecimal("100.00")).horaApertura(OffsetDateTime.now(FechaNegocio.ZONA_LIMA))
                .build());

        // Egresos ya se insertan con monto NEGATIVO (mismo signo que el seed, V4__seed.sql) --
        // "menos egresos" ya está en el signo, no en una resta explícita del SQL.
        cajaDao.insertarMovimiento(MovimientoCaja.builder()
                .boticaId(boticaId).cajaId(caja.getId()).tipo("venta").descripcion("Ventas en efectivo")
                .monto(new BigDecimal("50.00")).afectaEfectivo(true).creadoPor(usuarioId).build());
        cajaDao.insertarMovimiento(MovimientoCaja.builder()
                .boticaId(boticaId).cajaId(caja.getId()).tipo("egreso").descripcion("Movilidad")
                .monto(new BigDecimal("-20.00")).afectaEfectivo(true).creadoPor(usuarioId).build());
        // Digital (yape/tarjeta): no afecta el efectivo en caja -- debe quedar fuera de la suma.
        cajaDao.insertarMovimiento(MovimientoCaja.builder()
                .boticaId(boticaId).cajaId(caja.getId()).tipo("venta").descripcion("Ventas por Yape")
                .monto(new BigDecimal("999.00")).afectaEfectivo(false).creadoPor(usuarioId).build());

        assertThat(cajaDao.sumarMovimientosEfectivo(boticaId, caja.getId()))
                .as("50.00 (venta efectivo) - 20.00 (egreso) = 30.00; los 999.00 digitales no cuentan")
                .isEqualByComparingTo("30.00");
    }
}
