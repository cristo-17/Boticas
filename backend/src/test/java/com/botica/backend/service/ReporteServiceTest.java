package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.ReporteDao;
import com.botica.backend.dto.FilaResultadoResponse;
import com.botica.backend.dto.ReporteResultadosResponse;
import com.botica.backend.dto.TotalesResultadoResponse;
import com.botica.backend.exception.FiltroInvalidoException;
import com.botica.backend.exception.RangoFechasInvalidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    @Mock
    private ReporteDao reporteDao;

    @Mock
    private ContextoOperacion contexto;

    private ReporteService reporteService;

    @BeforeEach
    void setUp() {
        reporteService = new ReporteService(reporteDao, contexto);
    }

    @Test
    void obtenerResultados_rechazaDesdeMayorQueHasta_400() {
        LocalDate desde = LocalDate.of(2026, 5, 10);
        LocalDate hasta = LocalDate.of(2026, 5, 9);

        assertThatThrownBy(() -> reporteService.obtenerResultados(1L, desde, hasta, "dia"))
                .isInstanceOf(RangoFechasInvalidoException.class)
                .hasMessageContaining("desde no puede ser mayor que hasta");
        verifyNoInteractions(reporteDao);
    }

    @Test
    void obtenerResultados_rechazaRangoMayorAUnAnio_400() {
        LocalDate desde = LocalDate.of(2025, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 1, 2);

        assertThatThrownBy(() -> reporteService.obtenerResultados(1L, desde, hasta, "dia"))
                .isInstanceOf(RangoFechasInvalidoException.class)
                .hasMessageContaining("1 año");
        verifyNoInteractions(reporteDao);
    }

    @Test
    void obtenerResultados_permiteRangoExactoDeUnAnio() {
        LocalDate desde = LocalDate.of(2025, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 1, 1);
        when(reporteDao.consultarFilasResultados(1L, desde, hasta, "mes")).thenReturn(List.of());
        when(reporteDao.consultarTotalesResultados(1L, desde, hasta)).thenReturn(new TotalesResultadoResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        ReporteResultadosResponse res = reporteService.obtenerResultados(1L, desde, hasta, "mes");
        assertThat(res.desde()).isEqualTo(desde);
        assertThat(res.hasta()).isEqualTo(hasta);
        assertThat(res.agrupar()).isEqualTo("mes");
        assertThat(res.nota()).isEqualTo(ReporteService.NOTA_REPORTE_INTERNO);
        assertThat(res.nota().toLowerCase()).doesNotContain("factura");
    }

    @Test
    void obtenerResultados_rechazaAgruparInvalido_400() {
        LocalDate desde = LocalDate.of(2026, 5, 1);
        LocalDate hasta = LocalDate.of(2026, 5, 10);

        assertThatThrownBy(() -> reporteService.obtenerResultados(1L, desde, hasta, "semana"))
                .isInstanceOf(FiltroInvalidoException.class)
                .hasMessageContaining("agrupar");
        verifyNoInteractions(reporteDao);
    }

    @Test
    void generarCsvResultados_empiezaConBomUtf8_incluyeTildesYFilaTotal() {
        LocalDate desde = LocalDate.of(2026, 5, 1);
        LocalDate hasta = LocalDate.of(2026, 5, 2);
        when(reporteDao.consultarFilasResultados(1L, desde, hasta, "dia")).thenReturn(List.of(
                new FilaResultadoResponse("2026-05-01",
                        new BigDecimal("100.00"), new BigDecimal("60.00"), new BigDecimal("40.00"),
                        new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("25.00")),
                new FilaResultadoResponse("2026-05-02",
                        new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                        new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"))
        ));
        when(reporteDao.consultarTotalesResultados(1L, desde, hasta)).thenReturn(new TotalesResultadoResponse(
                new BigDecimal("100.00"), new BigDecimal("60.00"), new BigDecimal("40.00"),
                new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("25.00")
        ));

        String csv = reporteService.generarCsvResultados(1L, desde, hasta, "dia");

        assertThat(csv).startsWith("\uFEFFPeríodo,Ventas (S/),Costo de ventas (S/),Margen bruto (S/),Mermas a costo (S/),Egresos de caja (S/),Resultado neto (S/)\r\n");
        assertThat(csv).contains("2026-05-01,100.00,60.00,40.00,5.00,10.00,25.00\r\n");
        assertThat(csv).contains("2026-05-02,0.00,0.00,0.00,0.00,0.00,0.00\r\n");
        assertThat(csv).endsWith("TOTAL,100.00,60.00,40.00,5.00,10.00,25.00\r\n");
    }
}
