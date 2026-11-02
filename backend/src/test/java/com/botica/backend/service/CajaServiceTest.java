package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.AbrirCajaRequest;
import com.botica.backend.dto.CajaResponse;
import com.botica.backend.dto.CerrarCajaRequest;
import com.botica.backend.dto.ResumenCierreResponse;
import com.botica.backend.exception.CajaNoAbiertaException;
import com.botica.backend.exception.CajaNoEncontradaException;
import com.botica.backend.exception.CajaYaAbiertaException;
import com.botica.backend.exception.MontoInvalidoException;
import com.botica.backend.exception.SinPermisoException;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CajaServiceTest {

    private static final Long BOTICA_ID = 1L;
    private static final Long USUARIO_ID = 1L;
    private static final LocalDate HOY = LocalDate.of(2026, 9, 8);

    @Mock
    private CajaDao cajaDao;
    @Mock
    private ContextoOperacion contexto;

    private ConfigNegocioProperties config;
    private CajaService service;

    @BeforeEach
    void configurar() {
        config = new ConfigNegocioProperties();
        Clock relojFijo = Clock.fixed(
                HOY.atTime(12, 0).atOffset(ZoneOffset.of("-05:00")).toInstant(),
                FechaNegocio.ZONA_LIMA);
        FechaNegocio fechaNegocio = new FechaNegocio(relojFijo);
        service = new CajaService(cajaDao, contexto, fechaNegocio, config);
    }

    @Test
    void abrir_conCajaYaAbierta_lanzaCajaYaAbiertaException() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID)).thenReturn(Optional.of(CajaDiaria.builder()
                .id(5L).boticaId(BOTICA_ID).usuarioId(USUARIO_ID).fecha(HOY).turno("Tarde")
                .montoApertura(new BigDecimal("100.00")).estado("ABIERTA").build()));

        AbrirCajaRequest request = new AbrirCajaRequest(new BigDecimal("100.00"), "Tarde");

        assertThatThrownBy(() -> service.abrir(request))
                .isInstanceOf(CajaYaAbiertaException.class);
        verify(cajaDao, never()).insertar(any());
    }

    // DIF-12 (Tarea 0B): una sola caja abierta por usuario, de cualquier día o turno
    // (DECISIONES 2026-09-08). Antes solo se miraba el mismo turno y día.
    @Test
    void abrir_conCajaAbiertaDeAyerEnOtroTurno_lanzaCajaYaAbierta_yElMensajeDiceCualCerrar() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        CajaDiaria deAyer = CajaDiaria.builder()
                .id(7L).boticaId(BOTICA_ID).usuarioId(USUARIO_ID).fecha(HOY.minusDays(1)).turno("Mañana")
                .montoApertura(new BigDecimal("100.00")).estado("ABIERTA").build();
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID)).thenReturn(Optional.of(deAyer));

        assertThatThrownBy(() -> service.abrir(new AbrirCajaRequest(new BigDecimal("100.00"), "Tarde")))
                .isInstanceOf(CajaYaAbiertaException.class)
                .hasMessage("Tienes abierta la caja del 07/09/2026, turno Mañana. Ciérrala antes de abrir otra.");
        verify(cajaDao, never()).insertar(any());
    }

    @Test
    void abrir_montoNegativo_lanzaMontoInvalidoException_antesDeConsultarElDao() {
        AbrirCajaRequest request = new AbrirCajaRequest(new BigDecimal("-1.00"), "Tarde");

        assertThatThrownBy(() -> service.abrir(request))
                .isInstanceOf(MontoInvalidoException.class);
        verify(cajaDao, never()).buscarAbiertaDelUsuario(any(), any());
    }

    @Test
    void abrir_exito_creaCajaConLosDatosDeContexto() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(cajaDao.insertar(any())).thenAnswer(inv -> {
            CajaDiaria c = inv.getArgument(0);
            c.setId(99L);
            c.setUsuarioNombre("Rosa Quispe");
            return c;
        });

        CajaResponse response = service.abrir(new AbrirCajaRequest(new BigDecimal("100.00"), "Tarde"));

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.abierta()).isTrue();
        assertThat(response.montoApertura()).isEqualByComparingTo("100.00");
        assertThat(response.fecha()).isEqualTo(HOY);
    }

    @Test
    void cerrar_sinCajaAbierta_lanzaCajaNoAbiertaException() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID)).thenReturn(Optional.empty());

        CerrarCajaRequest request = new CerrarCajaRequest(new BigDecimal("100.00"), null, null);

        assertThatThrownBy(() -> service.cerrar(request))
                .isInstanceOf(CajaNoAbiertaException.class);
    }

    @Test
    void cerrar_montoNegativo_lanzaMontoInvalidoException_antesDeBuscarLaCaja() {
        CerrarCajaRequest request = new CerrarCajaRequest(new BigDecimal("-5.00"), null, null);

        assertThatThrownBy(() -> service.cerrar(request))
                .isInstanceOf(MontoInvalidoException.class);
        verify(cajaDao, never()).buscarAbiertaDelUsuario(any(), any());
    }

    @Test
    void cerrar_montoContadoIgualAlEsperado_daSemaforoExacto() {
        CajaDiaria abierta = cajaAbierta();
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID)).thenReturn(Optional.of(abierta));
        when(cajaDao.sumarMovimientosEfectivo(BOTICA_ID, abierta.getId())).thenReturn(new BigDecimal("12.20"));

        CajaResponse response = service.cerrar(new CerrarCajaRequest(new BigDecimal("112.20"), null, null));

        assertThat(response.montoEsperado()).isEqualByComparingTo("112.20");
        assertThat(response.diferencia()).isEqualByComparingTo("0.00");
        assertThat(response.semaforoDescuadre()).isEqualTo("EXACTO");
        assertThat(response.abierta()).isFalse();
        verify(cajaDao).actualizarCierre(any());
    }

    @Test
    void cerrar_descuadreDentroDelUmbral_daSemaforoLeve() {
        CajaDiaria abierta = cajaAbierta();
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID)).thenReturn(Optional.of(abierta));
        when(cajaDao.sumarMovimientosEfectivo(BOTICA_ID, abierta.getId())).thenReturn(new BigDecimal("0.00"));

        CajaResponse response = service.cerrar(new CerrarCajaRequest(new BigDecimal("95.00"), "faltante", null));

        assertThat(response.diferencia()).isEqualByComparingTo("-5.00");
        assertThat(response.semaforoDescuadre()).isEqualTo("LEVE");
    }

    @Test
    void cerrar_descuadreFueraDelUmbral_daSemaforoGrave() {
        CajaDiaria abierta = cajaAbierta();
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID)).thenReturn(Optional.of(abierta));
        when(cajaDao.sumarMovimientosEfectivo(BOTICA_ID, abierta.getId())).thenReturn(new BigDecimal("0.00"));

        CajaResponse response = service.cerrar(new CerrarCajaRequest(new BigDecimal("60.00"), null, null));

        assertThat(response.diferencia()).isEqualByComparingTo("-40.00");
        assertThat(response.semaforoDescuadre()).isEqualTo("GRAVE");
    }

    @Test
    void obtenerResumenCierre_cajaInexistente_lanzaCajaNoEncontradaException() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(cajaDao.buscarPorId(BOTICA_ID, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerResumenCierre(5L))
                .isInstanceOf(CajaNoEncontradaException.class);
    }

    @Test
    void obtenerResumenCierre_cajaYaCerrada_lanzaCajaNoAbiertaException() {
        CajaDiaria cerrada = cajaAbierta();
        cerrada.setEstado(CajaDiaria.CERRADA);
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(cajaDao.buscarPorId(BOTICA_ID, cerrada.getId())).thenReturn(Optional.of(cerrada));

        assertThatThrownBy(() -> service.obtenerResumenCierre(cerrada.getId()))
                .isInstanceOf(CajaNoAbiertaException.class);
    }

    @Test
    void obtenerResumenCierre_cajaAbierta_noIncluyeMontoEsperado() {
        CajaDiaria abierta = cajaAbierta();
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(cajaDao.buscarPorId(BOTICA_ID, abierta.getId())).thenReturn(Optional.of(abierta));
        ResumenCierreResponse esperado = new ResumenCierreResponse(new BigDecimal("20.00"), 3L, 4L);
        when(cajaDao.calcularResumen(BOTICA_ID, abierta.getId())).thenReturn(esperado);

        ResumenCierreResponse response = service.obtenerResumenCierre(abierta.getId());

        assertThat(response.totalVentasDigital()).isEqualByComparingTo("20.00");
        assertThat(response.cantidadVentas()).isEqualTo(3L);
        assertThat(response.cantidadMovimientos()).isEqualTo(4L);
    }

    @Test
    void abrir_alas11pmHoraLima_usaElDiaDeLima_noElDeUtcQueYaCambioDeDia() {
        LocalDate hoyLima = LocalDate.of(2026, 9, 8);
        Instant instante2300Lima = hoyLima.atTime(23, 0).atOffset(ZoneOffset.of("-05:00")).toInstant();
        FechaNegocio fechaNegocio = new FechaNegocio(Clock.fixed(instante2300Lima, FechaNegocio.ZONA_LIMA));
        CajaService servicioTardio = new CajaService(cajaDao, contexto, fechaNegocio, config);
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(cajaDao.insertar(any())).thenAnswer(inv -> {
            CajaDiaria c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        servicioTardio.abrir(new AbrirCajaRequest(new BigDecimal("50.00"), "Noche"));

        verify(cajaDao).buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID);
        verify(cajaDao).insertar(argThat(c -> c.getFecha().equals(hoyLima)));
    }

    // D-E2-1 (DECISIONES 2026-10-14): el TECNICO cierra solo la suya; el ADMINISTRADOR, cualquier caja abierta de su botica.
    @Test
    void cerrar_administradorConCajaIdDeOtroUsuario_laCierra() {
        CajaDiaria ajena = cajaAbiertaDe(2L);
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.rol()).thenReturn("ADMINISTRADOR");
        when(cajaDao.buscarPorId(BOTICA_ID, ajena.getId())).thenReturn(Optional.of(ajena));
        when(cajaDao.sumarMovimientosEfectivo(BOTICA_ID, ajena.getId())).thenReturn(new BigDecimal("0.00"));

        CajaResponse response = service.cerrar(new CerrarCajaRequest(new BigDecimal("100.00"), null, ajena.getId()));

        assertThat(response.abierta()).isFalse();
        verify(cajaDao).actualizarCierre(argThat(c -> c.getId().equals(ajena.getId())));
    }

    @Test
    void cerrar_tecnicoConCajaIdDeOtroUsuario_lanzaSinPermiso_yNoLaToca() {
        CajaDiaria ajena = cajaAbiertaDe(2L);
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(contexto.rol()).thenReturn("TECNICO");
        when(cajaDao.buscarPorId(BOTICA_ID, ajena.getId())).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.cerrar(new CerrarCajaRequest(new BigDecimal("100.00"), null, ajena.getId())))
                .isInstanceOf(SinPermisoException.class);
        verify(cajaDao, never()).actualizarCierre(any());
    }

    @Test
    void cerrar_administradorConCajaIdDeOtraBotica_noLaEncuentra() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(cajaDao.buscarPorId(BOTICA_ID, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cerrar(new CerrarCajaRequest(new BigDecimal("100.00"), null, 99L)))
                .isInstanceOf(CajaNoEncontradaException.class);
        verify(cajaDao, never()).actualizarCierre(any());
    }

    @Test
    void cerrar_administradorConCajaIdYaCerrada_lanzaCajaNoAbierta() {
        CajaDiaria cerrada = cajaAbiertaDe(2L);
        cerrada.setEstado(CajaDiaria.CERRADA);
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.rol()).thenReturn("ADMINISTRADOR");
        when(cajaDao.buscarPorId(BOTICA_ID, cerrada.getId())).thenReturn(Optional.of(cerrada));

        assertThatThrownBy(() -> service.cerrar(new CerrarCajaRequest(new BigDecimal("100.00"), null, cerrada.getId())))
                .isInstanceOf(CajaNoAbiertaException.class);
    }

    private CajaDiaria cajaAbiertaDe(Long usuarioId) {
        CajaDiaria caja = cajaAbierta();
        caja.setId(20L);
        caja.setUsuarioId(usuarioId);
        return caja;
    }

    private CajaDiaria cajaAbierta() {
        return CajaDiaria.builder()
                .id(10L)
                .boticaId(BOTICA_ID)
                .usuarioId(USUARIO_ID)
                .usuarioNombre("Rosa Quispe")
                .fecha(HOY)
                .turno("Tarde")
                .montoApertura(new BigDecimal("100.00"))
                .horaApertura(OffsetDateTime.now())
                .estado(CajaDiaria.ABIERTA)
                .build();
    }
}
