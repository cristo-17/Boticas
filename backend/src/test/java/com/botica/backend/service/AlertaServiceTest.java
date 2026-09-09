package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.AlertaDao;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.AlertaResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ResumenDashboardResponse;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertaServiceTest {

    private static final Long BOTICA_ID = 1L;
    private static final Long USUARIO_ID = 7L;
    private static final LocalDate HOY = LocalDate.of(2026, 9, 9);

    @Mock
    private AlertaDao alertaDao;
    @Mock
    private CajaDao cajaDao;
    @Mock
    private ContextoOperacion contexto;

    private ConfigNegocioProperties config;
    private AlertaService service;

    @BeforeEach
    void configurar() {
        config = new ConfigNegocioProperties();
        Clock relojFijo = Clock.fixed(HOY.atTime(12, 0).atOffset(ZoneOffset.of("-05:00")).toInstant(), FechaNegocio.ZONA_LIMA);
        service = new AlertaService(alertaDao, cajaDao, contexto, config, new FechaNegocio(relojFijo));
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
    }

    @Test
    void resumen_conCajaAbiertaDelUsuario_reportaCajaEstadoAbierta() {
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(alertaDao.totalVentas(BOTICA_ID, HOY)).thenReturn(new BigDecimal("2964.50"));
        when(alertaDao.totalVentas(BOTICA_ID, HOY.minusDays(1))).thenReturn(new BigDecimal("2647.77"));
        when(alertaDao.contarVentas(BOTICA_ID, HOY)).thenReturn(64L);
        when(alertaDao.contarProductosPorVencer(BOTICA_ID, HOY, 90)).thenReturn(14L);
        when(alertaDao.contarProductosPorVencer(BOTICA_ID, HOY, 30)).thenReturn(5L);
        when(alertaDao.contarProductosStockCritico(BOTICA_ID, 15)).thenReturn(6L);
        when(alertaDao.contarProductosStockAgotado(BOTICA_ID)).thenReturn(2L);
        OffsetDateTime apertura = OffsetDateTime.parse("2026-09-09T08:02:00-05:00");
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID))
                .thenReturn(Optional.of(CajaDiaria.builder().id(1001L).horaApertura(apertura).build()));

        ResumenDashboardResponse resumen = service.resumen();

        assertThat(resumen.cajaEstado()).isEqualTo("ABIERTA");
        assertThat(resumen.cajaHoraApertura()).isEqualTo(apertura);
        assertThat(resumen.productosPorVencer()).isEqualTo(14L);
        assertThat(resumen.productosPorVencerCriticos()).isEqualTo(5L);
        assertThat(resumen.stockCritico()).isEqualTo(6L);
        assertThat(resumen.stockAgotado()).isEqualTo(2L);
        assertThat(resumen.ventasHoyVariacionPct()).isEqualTo(12); // (2964.50-2647.77)/2647.77 ~ 11.96% -> 12
    }

    @Test
    void resumen_sinCajaAbiertaDelUsuario_reportaCajaEstadoCerrada() {
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(alertaDao.totalVentas(BOTICA_ID, HOY)).thenReturn(BigDecimal.ZERO);
        when(alertaDao.totalVentas(BOTICA_ID, HOY.minusDays(1))).thenReturn(BigDecimal.ZERO);
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID)).thenReturn(Optional.empty());

        ResumenDashboardResponse resumen = service.resumen();

        assertThat(resumen.cajaEstado()).isEqualTo("CERRADA");
        assertThat(resumen.cajaHoraApertura()).isNull();
        assertThat(resumen.ventasHoyVariacionPct()).isEqualTo(0); // ayer=0 -> sin división por cero
    }

    @Test
    void listar_ordenaUrgentesAntesQueAtencion_yPagina() {
        var loteVencido = new AlertaDao.LoteAlerta(6L, "Omeprazol 20 mg", "L-2311D", "B-2", 7,
                HOY.minusDays(15), new BigDecimal("11.50"), "VENCIDO");
        when(alertaDao.lotesVencidosOCriticos(BOTICA_ID, HOY, 30)).thenReturn(List.of(loteVencido));
        when(alertaDao.contarProductosStockCritico(BOTICA_ID, 15)).thenReturn(6L);
        when(alertaDao.contarProductosStockAgotado(BOTICA_ID)).thenReturn(0L);
        when(alertaDao.nombresProductosStockBajo(BOTICA_ID, 15, 2)).thenReturn(List.of("Amoxicilina", "Clotrimazol"));
        var cajaAbierta = new AlertaDao.CajaAbierta(1001L, "Mañana", "Rosa Quispe", OffsetDateTime.parse("2026-09-08T08:02:00-05:00"));
        when(alertaDao.cajasSinCerrar(BOTICA_ID, HOY)).thenReturn(List.of(cajaAbierta));

        PaginaResponse<AlertaResponse> pagina = service.listar(0, 20);

        assertThat(pagina.totalElementos()).isEqualTo(3);
        assertThat(pagina.contenido().get(0).nivel()).isEqualTo("urgente");
        assertThat(pagina.contenido().get(0).id()).isEqualTo("venc-6");
        assertThat(pagina.contenido().get(0).accionQueryParams()).containsEntry("vencimiento", "vencido");
        // Conteo ciego (hueco 2): la alerta de caja sin cerrar nunca expone un monto esperado.
        AlertaResponse alertaCaja = pagina.contenido().stream().filter(a -> a.id().equals("caja-1001")).findFirst().orElseThrow();
        assertThat(alertaCaja.monto()).isNull();
        assertThat(alertaCaja.titulo()).isEqualTo("Caja sin cerrar del turno mañana");
    }

    @Test
    void listar_sinProductosEnStockBajo_noGeneraLaAlertaAgregada() {
        when(alertaDao.lotesVencidosOCriticos(BOTICA_ID, HOY, 30)).thenReturn(List.of());
        when(alertaDao.contarProductosStockCritico(BOTICA_ID, 15)).thenReturn(0L);
        when(alertaDao.contarProductosStockAgotado(BOTICA_ID)).thenReturn(0L);
        when(alertaDao.cajasSinCerrar(BOTICA_ID, HOY)).thenReturn(List.of());

        PaginaResponse<AlertaResponse> pagina = service.listar(0, 20);

        assertThat(pagina.contenido()).isEmpty();
    }
}
