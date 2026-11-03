package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.ReporteDao;
import com.botica.backend.dto.EgresoPorMotivoResponse;
import com.botica.backend.dto.FilaResultadoResponse;
import com.botica.backend.dto.ReporteResultadosResponse;
import com.botica.backend.dto.TotalesResultadoResponse;
import com.botica.backend.exception.FiltroInvalidoException;
import com.botica.backend.exception.RangoFechasInvalidoException;
import com.botica.backend.util.FechaNegocio;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
        // 2026-11-03T02:15Z = 2026-11-02 21:15 en Lima: el servidor en UTC ya cambió de día.
        reporteService = new ReporteService(reporteDao, contexto,
                new FechaNegocio(Clock.fixed(Instant.parse("2026-11-03T02:15:00Z"), FechaNegocio.ZONA_LIMA)));
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
        when(reporteDao.consultarTotalesResultados(1L, desde, hasta)).thenReturn(cero());

        ReporteResultadosResponse res = reporteService.obtenerResultados(1L, desde, hasta, "mes");
        assertThat(res.desde()).isEqualTo(desde);
        assertThat(res.hasta()).isEqualTo(hasta);
        assertThat(res.agrupar()).isEqualTo("mes");
        assertThat(res.nota()).isEqualTo(ReporteService.NOTA_REPORTE_INTERNO);
        assertThat(res.nota().toLowerCase()).doesNotContain("factura");
    }

    @Test
    void obtenerResultados_aceptaSemana_yLaPasaAlDao() {
        LocalDate desde = LocalDate.of(2026, 11, 3);
        LocalDate hasta = LocalDate.of(2026, 11, 12);
        when(reporteDao.consultarFilasResultados(1L, desde, hasta, "semana")).thenReturn(List.of());
        when(reporteDao.consultarTotalesResultados(1L, desde, hasta)).thenReturn(cero());

        assertThat(reporteService.obtenerResultados(1L, desde, hasta, " SEMANA ").agrupar()).isEqualTo("semana");
    }

    @Test
    void obtenerResultados_rechazaAgruparInvalido_400() {
        LocalDate desde = LocalDate.of(2026, 5, 1);
        LocalDate hasta = LocalDate.of(2026, 5, 10);

        assertThatThrownBy(() -> reporteService.obtenerResultados(1L, desde, hasta, "anio"))
                .isInstanceOf(FiltroInvalidoException.class)
                .hasMessageContaining("agrupar");
        verifyNoInteractions(reporteDao);
    }

    @Test
    void generarExcel_tieneHojasFormatoValoresDelServidorTotalGraficoYEgresosPorMotivo() throws Exception {
        LocalDate desde = LocalDate.of(2026, 11, 3);
        LocalDate hasta = LocalDate.of(2026, 11, 4);
        when(reporteDao.consultarFilasResultados(1L, desde, hasta, "dia")).thenReturn(List.of(
                new FilaResultadoResponse("2026-11-03", "03/11/2026",
                        new BigDecimal("100.10"), new BigDecimal("60.05"), new BigDecimal("40.05"),
                        new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("75.05"), new BigDecimal("25.05")),
                new FilaResultadoResponse("2026-11-04", "04/11/2026",
                        new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                        new BigDecimal("0.00"), new BigDecimal("12.34"), new BigDecimal("12.34"), new BigDecimal("-12.34"))
        ));
        when(reporteDao.consultarTotalesResultados(1L, desde, hasta)).thenReturn(new TotalesResultadoResponse(
                new BigDecimal("100.10"), new BigDecimal("60.05"), new BigDecimal("40.05"),
                new BigDecimal("5.00"), new BigDecimal("22.34"), new BigDecimal("87.39"), new BigDecimal("12.71")));
        when(reporteDao.consultarEgresosPorMotivo(1L, desde, hasta)).thenReturn(List.of(
                new EgresoPorMotivoResponse("Servicios básicos", 2, new BigDecimal("12.34")),
                new EgresoPorMotivoResponse("Movilidad", 1, new BigDecimal("10.00"))));
        when(reporteDao.consultarNombreBotica(1L)).thenReturn("Botica San Martín");

        byte[] xlsx = reporteService.generarExcelResultados(1L, desde, hasta, "dia");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheetName(0)).isEqualTo("Resultados");
            assertThat(wb.getSheetName(1)).isEqualTo("Gráfico");
            assertThat(wb.getSheetName(2)).isEqualTo("Egresos por motivo");

            XSSFSheet hoja = wb.getSheet("Resultados");
            assertThat(texto(hoja, 0, 0)).isEqualTo("Botica San Martín");
            assertThat(texto(hoja, 1, 0)).contains("03/11/2026 al 04/11/2026");
            assertThat(texto(hoja, 2, 0)).isEqualTo("Agrupación: Día");
            assertThat(texto(hoja, 3, 0)).isEqualTo("Generado: 02/11/2026 21:15 (hora de Lima)");
            assertThat(texto(hoja, 4, 0)).isEqualTo(ReporteService.NOTA_REPORTE_INTERNO);

            int enc = ReporteExcel.FILA_ENCABEZADO;
            assertThat(texto(hoja, enc, 0)).isEqualTo("Período");
            assertThat(texto(hoja, enc, 6)).isEqualTo("Gastos");
            XSSFCell celdaEnc = hoja.getRow(enc).getCell(1);
            assertThat(celdaEnc.getCellStyle().getFont().getBold()).isTrue();
            assertThat(celdaEnc.getCellStyle().getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);

            // Valores (no fórmulas) iguales al céntimo a los del servidor.
            XSSFCell ventas = hoja.getRow(enc + 1).getCell(1);
            assertThat(ventas.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(BigDecimal.valueOf(ventas.getNumericCellValue())).isEqualByComparingTo("100.10");
            assertThat(ventas.getCellStyle().getDataFormatString()).isEqualTo(ReporteExcel.FORMATO_SOLES);
            assertThat(ReporteExcel.FORMATO_SOLES).contains("[Red]").startsWith("\"S/ \"#,##0.00");
            assertThat(texto(hoja, enc + 1, 0)).isEqualTo("03/11/2026");
            assertThat(BigDecimal.valueOf(hoja.getRow(enc + 2).getCell(7).getNumericCellValue()))
                    .isEqualByComparingTo("-12.34");

            XSSFRow total = hoja.getRow(enc + 3);
            assertThat(texto(hoja, enc + 3, 0)).isEqualTo("TOTAL");
            assertThat(total.getCell(0).getCellStyle().getFont().getBold()).isTrue();
            assertThat(total.getCell(7).getCellStyle().getFont().getBold()).isTrue();
            assertThat(BigDecimal.valueOf(total.getCell(6).getNumericCellValue())).isEqualByComparingTo("87.39");
            assertThat(BigDecimal.valueOf(total.getCell(7).getNumericCellValue())).isEqualByComparingTo("12.71");
            for (Row row : hoja) {
                for (Cell c : row) {
                    assertThat(c.getCellType()).isNotEqualTo(CellType.FORMULA);
                }
            }

            assertThat(hoja.getPaneInformation().isFreezePane()).isTrue();
            assertThat(hoja.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) (enc + 1));
            assertThat(hoja.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A7:H9");
            assertThat(hoja.getColumnWidth(1)).isGreaterThan(hoja.getDefaultColumnWidth() * 256);

            List<XSSFChart> charts = wb.getSheet("Gráfico").getDrawingPatriarch().getCharts();
            assertThat(charts).hasSize(1);
            // Barras (ingresos, gastos) + línea (resultado) sobre los mismos ejes.
            assertThat(charts.get(0).getChartSeries()).hasSize(2);
            assertThat(charts.get(0).getChartSeries().get(0).getSeriesCount()).isEqualTo(2);
            assertThat(charts.get(0).getChartSeries().get(1).getSeriesCount()).isEqualTo(1);

            XSSFSheet egresos = wb.getSheet("Egresos por motivo");
            int fe = buscarFila(egresos, "Motivo");
            assertThat(texto(egresos, fe + 1, 0)).isEqualTo("Servicios básicos");
            assertThat(egresos.getRow(fe + 1).getCell(1).getNumericCellValue()).isEqualTo(2d);
            assertThat(texto(egresos, fe + 3, 0)).isEqualTo("TOTAL");
            assertThat(egresos.getRow(fe + 3).getCell(1).getNumericCellValue()).isEqualTo(3d);
            assertThat(BigDecimal.valueOf(egresos.getRow(fe + 3).getCell(2).getNumericCellValue()))
                    .isEqualByComparingTo("22.34");
        }
        assertThat(ReporteService.nombreArchivoExcel(desde, hasta))
                .isEqualTo("reporte-resultados_2026-11-03_2026-11-04.xlsx");
    }

    @Test
    void generarExcel_sinFilasNiEgresos_noFalla_yRotulaSemana() throws Exception {
        LocalDate desde = LocalDate.of(2026, 11, 3);
        LocalDate hasta = LocalDate.of(2026, 11, 5);
        when(reporteDao.consultarFilasResultados(1L, desde, hasta, "semana")).thenReturn(List.of());
        when(reporteDao.consultarTotalesResultados(1L, desde, hasta)).thenReturn(cero());
        when(reporteDao.consultarEgresosPorMotivo(1L, desde, hasta)).thenReturn(List.of());
        when(reporteDao.consultarNombreBotica(1L)).thenReturn("Botica");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(
                reporteService.generarExcelResultados(1L, desde, hasta, "semana")))) {
            assertThat(texto(wb.getSheet("Resultados"), 2, 0)).isEqualTo("Agrupación: Semana (lunes a domingo)");
            XSSFSheet egresos = wb.getSheet("Egresos por motivo");
            int fe = buscarFila(egresos, "Motivo");
            assertThat(texto(egresos, fe + 1, 0)).isEqualTo("Sin egresos en el rango");
        }
    }

    private static TotalesResultadoResponse cero() {
        return new TotalesResultadoResponse(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static String texto(XSSFSheet hoja, int fila, int col) {
        return hoja.getRow(fila).getCell(col).getStringCellValue();
    }

    private static int buscarFila(XSSFSheet hoja, String valorColA) {
        for (Row row : hoja) {
            Cell c = row.getCell(0);
            if (c != null && c.getCellType() == CellType.STRING && valorColA.equals(c.getStringCellValue())) {
                return row.getRowNum();
            }
        }
        throw new AssertionError("No se encontró la fila " + valorColA);
    }
}
