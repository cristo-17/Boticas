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
import static org.mockito.ArgumentMatchers.anyString;
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
        // Clock fijo, no Mockito, para que fechaNegocio.hoy()/ahora() sean deterministas de verdad.
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
        when(cajaDao.existeCajaAbierta(BOTICA_ID, USUARIO_ID, "Tarde", HOY)).thenReturn(true);

        AbrirCajaRequest request = new AbrirCajaRequest(new BigDecimal("100.00"), "Tarde");

        assertThatThrownBy(() -> service.abrir(request))
                .isInstanceOf(CajaYaAbiertaException.class);
        verify(cajaDao, never()).insertar(any());
    }

    @Test
    void abrir_montoNegativo_lanzaMontoInvalidoException_antesDeConsultarElDao() {
        AbrirCajaRequest request = new AbrirCajaRequest(new BigDecimal("-1.00"), "Tarde");

        assertThatThrownBy(() -> service.abrir(request))
                .isInstanceOf(MontoInvalidoException.class);
        verify(cajaDao, never()).existeCajaAbierta(any(), any(), anyString(), any());
    }

    @Test
    void abrir_exito_creaCajaConLosDatosDeContexto() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(cajaDao.existeCajaAbierta(BOTICA_ID, USUARIO_ID, "Tarde", HOY)).thenReturn(false);
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

        CerrarCajaRequest request = new CerrarCajaRequest(new BigDecimal("100.00"), null);

        assertThatThrownBy(() -> service.cerrar(request))
                .isInstanceOf(CajaNoAbiertaException.class);
    }

    @Test
    void cerrar_montoNegativo_lanzaMontoInvalidoException_antesDeBuscarLaCaja() {
        CerrarCajaRequest request = new CerrarCajaRequest(new BigDecimal("-5.00"), null);

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

        CajaResponse response = service.cerrar(new CerrarCajaRequest(new BigDecimal("112.20"), null));

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
        // esperado = 100.00 (apertura) + 0 = 100.00; contado 95.00 -> diferencia -5.00, dentro de 10.00
        CajaResponse response = service.cerrar(new CerrarCajaRequest(new BigDecimal("95.00"), "faltante"));

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
        // esperado = 100.00; contado 60.00 -> diferencia -40.00, fuera de 10.00
        CajaResponse response = service.cerrar(new CerrarCajaRequest(new BigDecimal("60.00"), null));

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
        ResumenCierreResponse esperado = new ResumenCierreResponse(
                new BigDecimal("50.00"), new BigDecimal("20.00"), 3L, 4L);
        when(cajaDao.calcularResumen(BOTICA_ID, abierta.getId())).thenReturn(esperado);

        ResumenCierreResponse response = service.obtenerResumenCierre(abierta.getId());

        // Conteo ciego (hueco 2): el tipo mismo no tiene campo montoEsperado -- si algún día
        // alguien lo agrega a ResumenCierreResponse, este assert de campos concretos sigue
        // documentando la intención sin depender de reflexión.
        assertThat(response.totalVentasEfectivo()).isEqualByComparingTo("50.00");
        assertThat(response.totalVentasDigital()).isEqualByComparingTo("20.00");
        assertThat(response.cantidadVentas()).isEqualTo(3L);
        assertThat(response.cantidadMovimientos()).isEqualTo(4L);
    }

    @Test
    void abrir_alas11pmHoraLima_usaElDiaDeLima_noElDeUtcQueYaCambioDeDia() {
        // 2026-09-08 23:00 America/Lima == 2026-09-09 04:00 UTC (Lima es UTC-5 fijo) -- el bug
        // clásico (docs/BITACORA.md) es que un servidor en UTC ya "cree" que es el día siguiente.
        LocalDate hoyLima = LocalDate.of(2026, 9, 8);
        Instant instante2300Lima = hoyLima.atTime(23, 0).atOffset(ZoneOffset.of("-05:00")).toInstant();
        FechaNegocio fechaNegocio = new FechaNegocio(Clock.fixed(instante2300Lima, FechaNegocio.ZONA_LIMA));
        CajaService servicioTardio = new CajaService(cajaDao, contexto, fechaNegocio, config);
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(cajaDao.existeCajaAbierta(BOTICA_ID, USUARIO_ID, "Noche", hoyLima)).thenReturn(false);
        when(cajaDao.insertar(any())).thenAnswer(inv -> {
            CajaDiaria c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        servicioTardio.abrir(new AbrirCajaRequest(new BigDecimal("50.00"), "Noche"));

        // Si el bug reapareciera (zona horaria UTC en vez de Lima), este mock nunca matchearía
        // (se llamaría con 2026-09-09, no con hoyLima) y el test fallaría con UnnecessaryStubbing
        // o un NPE al no encontrar respuesta configurada para esos argumentos.
        verify(cajaDao).existeCajaAbierta(BOTICA_ID, USUARIO_ID, "Noche", hoyLima);
        verify(cajaDao).insertar(argThat(c -> c.getFecha().equals(hoyLima)));
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
