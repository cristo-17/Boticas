package com.botica.backend.service;

import com.botica.backend.dto.EgresoPorMotivoResponse;
import com.botica.backend.dto.FilaResultadoResponse;
import com.botica.backend.dto.ReporteResultadosResponse;
import com.botica.backend.dto.TotalesResultadoResponse;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El .xlsx debe generarse en el contenedor de Render (Linux, sin pantalla) igual
 * que en Windows. {@code autoSizeColumn} y cualquier medición de texto con
 * {@code java.awt} dependen de las fuentes instaladas y pueden dar 500 allá
 * aunque acá funcione; por eso {@link ReporteExcel} calcula los anchos por largo
 * de texto.
 *
 * <p>Este test lo demuestra en una JVM aparte, arrancada con
 * {@code -Djava.awt.headless=true} y registro de carga de clases: genera el
 * archivo y falla si se cargó alguna clase de fuentes o de entorno gráfico
 * ({@code sun.font.*}, {@code java.awt.Font}, {@code java.awt.GraphicsEnvironment},
 * {@code java.awt.Toolkit}). En la misma JVM de la suite no serviría: otra
 * prueba podría haber cargado esas clases antes.</p>
 */
class ReporteExcelHeadlessTest {

    private static final List<String> CLASES_PROHIBIDAS = List.of(
            "sun.font.", "java.awt.Font ", "java.awt.GraphicsEnvironment ", "java.awt.Toolkit ",
            "java.awt.font.");

    @Test
    void generaElXlsxEnJvmHeadless_sinCargarFuentesNiAwt() throws Exception {
        Path tmp = Files.createTempDirectory("xlsx-headless");
        Path salida = tmp.resolve("reporte.xlsx");
        Path log = tmp.resolve("clases.log");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();

        Process p = new ProcessBuilder(java,
                "-Djava.awt.headless=true",
                "-Xlog:class+load=info:file=" + log.toAbsolutePath(),
                "-cp", classpath,
                Generador.class.getName(), salida.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        String consola = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).as("la JVM hija terminó").isTrue();
        assertThat(p.exitValue()).as("salida de la JVM hija:%n%s", consola).isZero();
        assertThat(consola).contains("headless=true");

        List<String> cargadas = Files.readAllLines(log);
        assertThat(cargadas).as("se registró la carga de clases").anyMatch(l -> l.contains("ReporteExcel "));
        for (String prohibida : CLASES_PROHIBIDAS) {
            assertThat(cargadas).as("no debe cargar %s", prohibida.trim())
                    .noneMatch(l -> l.contains(" " + prohibida));
        }

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(Files.readAllBytes(salida)))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(3);
            XSSFSheet hoja = wb.getSheet(ReporteExcel.HOJA_RESULTADOS);
            // Ancho por largo de texto: la columna Ventas alcanza para "S/ 1,234,567.89" (15) + margen.
            assertThat(hoja.getColumnWidth(1)).isEqualTo((15 + ReporteExcel.MARGEN_ANCHO) * 256);
            // Período: "03/11 al 08/11" (14) + margen.
            assertThat(hoja.getColumnWidth(0)).isEqualTo((14 + ReporteExcel.MARGEN_ANCHO) * 256);
            // Mermas a costo: manda el encabezado (14), más largo que "S/ 0.00" (7).
            assertThat(hoja.getColumnWidth(4)).isEqualTo(("Mermas a costo".length() + ReporteExcel.MARGEN_ANCHO) * 256);
            // Resultado neto: manda el monto negativo/positivo más largo, "S/ 1,232,567.89" (15).
            assertThat(hoja.getColumnWidth(7)).isEqualTo((15 + ReporteExcel.MARGEN_ANCHO) * 256);
        }
    }

    @Test
    void largoSoles_cuentaSignoPrefijoSeparadoresYDecimales() {
        assertThat(ReporteExcel.largoSoles(new BigDecimal("0"))).isEqualTo("S/ 0.00".length());
        assertThat(ReporteExcel.largoSoles(new BigDecimal("999.99"))).isEqualTo("S/ 999.99".length());
        assertThat(ReporteExcel.largoSoles(new BigDecimal("1000"))).isEqualTo("S/ 1,000.00".length());
        assertThat(ReporteExcel.largoSoles(new BigDecimal("-12345.6"))).isEqualTo("-S/ 12,345.60".length());
        assertThat(ReporteExcel.largoSoles(new BigDecimal("1234567.89"))).isEqualTo("S/ 1,234,567.89".length());
        assertThat(ReporteExcel.largoSoles(null)).isEqualTo("S/ 0.00".length());
    }

    /** Punto de entrada de la JVM hija. */
    public static final class Generador {
        public static void main(String[] args) throws Exception {
            System.out.println("headless=" + System.getProperty("java.awt.headless"));
            BigDecimal cero = new BigDecimal("0.00");
            List<FilaResultadoResponse> filas = List.of(
                    new FilaResultadoResponse("2026-11-03", "03/11 al 08/11",
                            new BigDecimal("1234567.89"), new BigDecimal("1000.00"), new BigDecimal("1233567.89"),
                            cero, cero, new BigDecimal("1000.00"), new BigDecimal("1233567.89")),
                    new FilaResultadoResponse("2026-11-09", "09/11 al 12/11",
                            cero, cero, cero, cero, new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                            new BigDecimal("-1000.00")));
            TotalesResultadoResponse totales = new TotalesResultadoResponse(
                    new BigDecimal("1234567.89"), new BigDecimal("1000.00"), new BigDecimal("1233567.89"),
                    cero, new BigDecimal("1000.00"), new BigDecimal("2000.00"), new BigDecimal("1232567.89"));
            ReporteResultadosResponse rep = new ReporteResultadosResponse(
                    LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 12), "semana",
                    "Reporte interno, sin valor tributario. Montos con IGV incluido.", filas, totales);
            byte[] xlsx = ReporteExcel.generar("Botica Headless", rep,
                    List.of(new EgresoPorMotivoResponse("Movilidad", 1, new BigDecimal("1000.00"))),
                    OffsetDateTime.of(2026, 11, 3, 2, 15, 0, 0, ZoneOffset.UTC));
            Files.write(Paths.get(args[0]), xlsx);
        }
    }
}
