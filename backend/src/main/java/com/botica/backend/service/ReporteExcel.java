package com.botica.backend.service;

import com.botica.backend.dto.EgresoPorMotivoResponse;
import com.botica.backend.dto.FilaResultadoResponse;
import com.botica.backend.dto.ReporteResultadosResponse;
import com.botica.backend.dto.TotalesResultadoResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xddf.usermodel.PresetColor;
import org.apache.poi.xddf.usermodel.XDDFColor;
import org.apache.poi.xddf.usermodel.XDDFLineProperties;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.apache.poi.xddf.usermodel.chart.AxisCrossBetween;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.MarkerStyle;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * Arma el .xlsx del reporte de resultados (Tarea 18, Bloque C) con Apache POI.
 *
 * <p>Solo da formato: todos los montos llegan ya calculados por el servidor
 * ({@link ReporteResultadosResponse}, SQL sobre NUMERIC) y se escriben como
 * <b>valores</b>, nunca fórmulas, para que coincidan al céntimo con la pantalla.
 * No usa {@code autoSizeColumn} ni nada de {@code java.awt}: medir texto con
 * fuentes AWT depende de lo que tenga instalado el contenedor y puede dar 500 en
 * Render aunque en Windows funcione. Cada ancho se calcula por el largo del texto
 * que va en la columna (montos ya formateados incluidos); lo comprueba
 * {@code ReporteExcelHeadlessTest} en una JVM aparte con {@code java.awt.headless=true}.</p>
 *
 * <p>Hojas: "Resultados" (tabla), "Gráfico" (barras ingresos vs gastos + línea
 * resultado, leyendo las celdas de "Resultados") y "Egresos por motivo".</p>
 */
final class ReporteExcel {

    static final String HOJA_RESULTADOS = "Resultados";
    static final String HOJA_GRAFICO = "Gráfico";
    static final String HOJA_EGRESOS = "Egresos por motivo";

    /** Fila (0-based) de los encabezados de la tabla en "Resultados". */
    static final int FILA_ENCABEZADO = 6;

    /** Caracteres extra por columna: flecha del autofiltro y negrita del TOTAL. */
    static final int MARGEN_ANCHO = 4;

    static final String FORMATO_SOLES = "\"S/ \"#,##0.00;[Red]\"-S/ \"#,##0.00";

    static final String[] ENCABEZADOS = {
            "Período", "Ventas", "Costo de ventas", "Margen bruto",
            "Mermas a costo", "Egresos de caja", "Gastos", "Resultado neto"
    };

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final byte[] VERDE_ENCABEZADO = {(byte) 0xD9, (byte) 0xEF, (byte) 0xE3};
    private static final byte[] GRIS_TOTAL = {(byte) 0xF0, (byte) 0xF2, (byte) 0xF4};
    private static final byte[] COLOR_INGRESOS = {(byte) 0x1E, (byte) 0x88, (byte) 0x5A};
    private static final byte[] COLOR_GASTOS = {(byte) 0xE0, (byte) 0x7A, (byte) 0x2E};

    private static final List<Function<FilaResultadoResponse, BigDecimal>> MONTOS_FILA = List.of(
            FilaResultadoResponse::ventas, FilaResultadoResponse::costoVentas,
            FilaResultadoResponse::margenBruto, FilaResultadoResponse::mermasCosto,
            FilaResultadoResponse::egresos, FilaResultadoResponse::gastos,
            FilaResultadoResponse::resultado);

    private static final List<Function<TotalesResultadoResponse, BigDecimal>> MONTOS_TOTAL = List.of(
            TotalesResultadoResponse::ventas, TotalesResultadoResponse::costoVentas,
            TotalesResultadoResponse::margenBruto, TotalesResultadoResponse::mermasCosto,
            TotalesResultadoResponse::egresos, TotalesResultadoResponse::gastos,
            TotalesResultadoResponse::resultado);

    private final XSSFWorkbook wb = new XSSFWorkbook();
    private final XSSFCellStyle estiloTitulo;
    private final XSSFCellStyle estiloMeta;
    private final XSSFCellStyle estiloNota;
    private final XSSFCellStyle estiloEncabezado;
    private final XSSFCellStyle estiloTexto;
    private final XSSFCellStyle estiloSoles;
    private final XSSFCellStyle estiloEntero;
    private final XSSFCellStyle estiloTotalTexto;
    private final XSSFCellStyle estiloTotalSoles;
    private final XSSFCellStyle estiloTotalEntero;

    private ReporteExcel() {
        short formatoSoles = wb.createDataFormat().getFormat(FORMATO_SOLES);
        short formatoEntero = wb.createDataFormat().getFormat("#,##0");

        XSSFFont fuenteTitulo = wb.createFont();
        fuenteTitulo.setBold(true);
        fuenteTitulo.setFontHeightInPoints((short) 14);
        estiloTitulo = wb.createCellStyle();
        estiloTitulo.setFont(fuenteTitulo);

        estiloMeta = wb.createCellStyle();

        XSSFFont fuenteNota = wb.createFont();
        fuenteNota.setItalic(true);
        fuenteNota.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        estiloNota = wb.createCellStyle();
        estiloNota.setFont(fuenteNota);

        XSSFFont negrita = wb.createFont();
        negrita.setBold(true);

        estiloEncabezado = wb.createCellStyle();
        estiloEncabezado.setFont(negrita);
        estiloEncabezado.setFillForegroundColor(new XSSFColor(VERDE_ENCABEZADO, null));
        estiloEncabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estiloEncabezado.setBorderBottom(BorderStyle.MEDIUM);
        estiloEncabezado.setAlignment(HorizontalAlignment.CENTER);
        estiloEncabezado.setVerticalAlignment(VerticalAlignment.CENTER);
        estiloEncabezado.setWrapText(true);

        estiloTexto = wb.createCellStyle();

        estiloSoles = wb.createCellStyle();
        estiloSoles.setDataFormat(formatoSoles);

        estiloEntero = wb.createCellStyle();
        estiloEntero.setDataFormat(formatoEntero);

        estiloTotalTexto = estiloTotal(negrita);
        estiloTotalSoles = estiloTotal(negrita);
        estiloTotalSoles.setDataFormat(formatoSoles);
        estiloTotalEntero = estiloTotal(negrita);
        estiloTotalEntero.setDataFormat(formatoEntero);
    }

    private XSSFCellStyle estiloTotal(XSSFFont negrita) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFont(negrita);
        s.setFillForegroundColor(new XSSFColor(GRIS_TOTAL, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderTop(BorderStyle.THIN);
        return s;
    }

    /**
     * @param generadoEn instante de generación ya en zona de Lima ({@code FechaNegocio.ahora()}).
     */
    static byte[] generar(String nombreBotica, ReporteResultadosResponse rep,
                          List<EgresoPorMotivoResponse> egresosPorMotivo, OffsetDateTime generadoEn) {
        ReporteExcel excel = new ReporteExcel();
        try (XSSFWorkbook wb = excel.wb) {
            wb.getProperties().getCoreProperties().setTitle("Reporte de resultados");
            wb.getProperties().getCoreProperties().setCreator("BoticaSys");
            XSSFSheet resultados = excel.hojaResultados(nombreBotica, rep, generadoEn);
            excel.hojaGrafico(resultados, rep);
            excel.hojaEgresos(nombreBotica, rep, egresosPorMotivo);
            wb.setActiveSheet(0);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel del reporte", e);
        }
    }

    static String nombreAgrupacion(String agrupar) {
        return switch (agrupar) {
            case "semana" -> "Semana (lunes a domingo)";
            case "mes" -> "Mes";
            default -> "Día";
        };
    }

    static String textoRango(LocalDate desde, LocalDate hasta) {
        return FMT_FECHA.format(desde) + " al " + FMT_FECHA.format(hasta);
    }

    private int cabecera(XSSFSheet hoja, String nombreBotica, ReporteResultadosResponse rep,
                         OffsetDateTime generadoEn) {
        texto(hoja.createRow(0), 0, nombreBotica, estiloTitulo);
        texto(hoja.createRow(1), 0, "Reporte de resultados · " + textoRango(rep.desde(), rep.hasta()), estiloMeta);
        texto(hoja.createRow(2), 0, "Agrupación: " + nombreAgrupacion(rep.agrupar()), estiloMeta);
        int fila = 3;
        if (generadoEn != null) {
            texto(hoja.createRow(fila++), 0,
                    "Generado: " + FMT_FECHA_HORA.format(generadoEn) + " (hora de Lima)", estiloMeta);
        }
        texto(hoja.createRow(fila++), 0, rep.nota(), estiloNota);
        return fila;
    }

    private XSSFSheet hojaResultados(String nombreBotica, ReporteResultadosResponse rep, OffsetDateTime generadoEn) {
        XSSFSheet hoja = wb.createSheet(HOJA_RESULTADOS);
        cabecera(hoja, nombreBotica, rep, generadoEn);

        XSSFRow enc = hoja.createRow(FILA_ENCABEZADO);
        enc.setHeightInPoints(30);
        for (int c = 0; c < ENCABEZADOS.length; c++) {
            texto(enc, c, ENCABEZADOS[c], estiloEncabezado);
        }

        int r = FILA_ENCABEZADO + 1;
        int[] largos = new int[ENCABEZADOS.length];
        for (int c = 0; c < ENCABEZADOS.length; c++) {
            largos[c] = ENCABEZADOS[c].length();
        }
        largos[0] = Math.max(largos[0], "TOTAL".length());
        for (FilaResultadoResponse f : rep.filas()) {
            XSSFRow row = hoja.createRow(r++);
            texto(row, 0, f.etiqueta(), estiloTexto);
            largos[0] = Math.max(largos[0], f.etiqueta().length());
            for (int i = 0; i < MONTOS_FILA.size(); i++) {
                BigDecimal monto = MONTOS_FILA.get(i).apply(f);
                soles(row, i + 1, monto, estiloSoles);
                largos[i + 1] = Math.max(largos[i + 1], largoSoles(monto));
            }
        }
        int ultimaFilaDatos = r - 1;

        XSSFRow total = hoja.createRow(r);
        texto(total, 0, "TOTAL", estiloTotalTexto);
        for (int i = 0; i < MONTOS_TOTAL.size(); i++) {
            BigDecimal monto = MONTOS_TOTAL.get(i).apply(rep.totales());
            soles(total, i + 1, monto, estiloTotalSoles);
            largos[i + 1] = Math.max(largos[i + 1], largoSoles(monto));
        }

        hoja.createFreezePane(1, FILA_ENCABEZADO + 1);
        if (!rep.filas().isEmpty()) {
            hoja.setAutoFilter(new CellRangeAddress(FILA_ENCABEZADO, ultimaFilaDatos, 0, ENCABEZADOS.length - 1));
        }
        for (int c = 0; c < ENCABEZADOS.length; c++) {
            // + margen para la flecha del autofiltro y la negrita del TOTAL
            hoja.setColumnWidth(c, ancho(largos[c] + MARGEN_ANCHO));
        }
        hoja.getPrintSetup().setLandscape(true);
        hoja.setFitToPage(true);
        hoja.getPrintSetup().setFitWidth((short) 1);
        hoja.getPrintSetup().setFitHeight((short) 0);
        return hoja;
    }

    private void hojaGrafico(XSSFSheet resultados, ReporteResultadosResponse rep) {
        XSSFSheet hoja = wb.createSheet(HOJA_GRAFICO);
        texto(hoja.createRow(0), 0, "Ingresos (ventas) contra gastos por período · " + textoRango(rep.desde(), rep.hasta()), estiloTitulo);
        texto(hoja.createRow(1), 0,
                "Gastos = costo de ventas + mermas a costo + egresos de caja. Datos de la hoja \"" + HOJA_RESULTADOS + "\".",
                estiloNota);
        if (rep.filas().isEmpty()) {
            return;
        }

        int primera = FILA_ENCABEZADO + 1;
        int ultima = FILA_ENCABEZADO + rep.filas().size();
        int anchoColumnas = Math.max(14, Math.min(40, rep.filas().size() + 4));

        XSSFDrawing dibujo = hoja.createDrawingPatriarch();
        XSSFClientAnchor ancla = dibujo.createAnchor(0, 0, 0, 0, 0, 3, anchoColumnas, 28);
        XSSFChart chart = dibujo.createChart(ancla);
        chart.setTitleText("Ingresos contra gastos");
        chart.setTitleOverlay(false);
        chart.getOrAddLegend().setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis ejeX = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis ejeY = chart.createValueAxis(AxisPosition.LEFT);
        ejeY.setCrosses(AxisCrosses.AUTO_ZERO);
        ejeY.setCrossBetween(AxisCrossBetween.BETWEEN);
        ejeY.setNumberFormat("\"S/ \"#,##0");

        XDDFDataSource<String> periodos = XDDFDataSourcesFactory.fromStringCellRange(
                resultados, new CellRangeAddress(primera, ultima, 0, 0));
        XDDFNumericalDataSource<Double> ventas = XDDFDataSourcesFactory.fromNumericCellRange(
                resultados, new CellRangeAddress(primera, ultima, 1, 1));
        XDDFNumericalDataSource<Double> gastos = XDDFDataSourcesFactory.fromNumericCellRange(
                resultados, new CellRangeAddress(primera, ultima, 6, 6));
        XDDFNumericalDataSource<Double> resultado = XDDFDataSourcesFactory.fromNumericCellRange(
                resultados, new CellRangeAddress(primera, ultima, 7, 7));

        XDDFBarChartData barras = (XDDFBarChartData) chart.createData(ChartTypes.BAR, ejeX, ejeY);
        barras.setBarDirection(BarDirection.COL);
        barras.setVaryColors(false);
        barras.setGapWidth(60);
        XDDFBarChartData.Series sIngresos = (XDDFBarChartData.Series) barras.addSeries(periodos, ventas);
        sIngresos.setTitle("Ingresos (ventas)", new CellReference(HOJA_RESULTADOS, FILA_ENCABEZADO, 1, true, true));
        sIngresos.setFillProperties(new XDDFSolidFillProperties(XDDFColor.from(COLOR_INGRESOS)));
        XDDFBarChartData.Series sGastos = (XDDFBarChartData.Series) barras.addSeries(periodos, gastos);
        sGastos.setTitle("Gastos", new CellReference(HOJA_RESULTADOS, FILA_ENCABEZADO, 6, true, true));
        sGastos.setFillProperties(new XDDFSolidFillProperties(XDDFColor.from(COLOR_GASTOS)));
        chart.plot(barras);

        XDDFLineChartData linea = (XDDFLineChartData) chart.createData(ChartTypes.LINE, ejeX, ejeY);
        linea.setVaryColors(false);
        XDDFLineChartData.Series sResultado = (XDDFLineChartData.Series) linea.addSeries(periodos, resultado);
        sResultado.setTitle("Resultado neto", new CellReference(HOJA_RESULTADOS, FILA_ENCABEZADO, 7, true, true));
        sResultado.setSmooth(false);
        sResultado.setMarkerStyle(MarkerStyle.CIRCLE);
        XDDFLineProperties trazo = new XDDFLineProperties(
                new XDDFSolidFillProperties(XDDFColor.from(PresetColor.DARK_SLATE_BLUE)));
        trazo.setWidth(2.25);
        sResultado.setLineProperties(trazo);
        chart.plot(linea);
    }

    private void hojaEgresos(String nombreBotica, ReporteResultadosResponse rep,
                             List<EgresoPorMotivoResponse> egresos) {
        XSSFSheet hoja = wb.createSheet(HOJA_EGRESOS);
        int r = cabecera(hoja, nombreBotica, rep, null) + 1;

        XSSFRow enc = hoja.createRow(r++);
        texto(enc, 0, "Motivo", estiloEncabezado);
        texto(enc, 1, "Cantidad", estiloEncabezado);
        texto(enc, 2, "Total", estiloEncabezado);
        int filaEncabezado = r - 1;

        int anchoMotivo = Math.max("Sin egresos en el rango".length(), "Motivo".length());
        int anchoTotal = Math.max("Total".length(), largoSoles(rep.totales().egresos()));
        long cantidadTotal = 0;
        if (egresos.isEmpty()) {
            texto(hoja.createRow(r++), 0, "Sin egresos en el rango", estiloNota);
        }
        for (EgresoPorMotivoResponse e : egresos) {
            XSSFRow row = hoja.createRow(r++);
            texto(row, 0, e.motivo(), estiloTexto);
            entero(row, 1, e.cantidad(), estiloEntero);
            soles(row, 2, e.total(), estiloSoles);
            anchoMotivo = Math.max(anchoMotivo, e.motivo().length());
            anchoTotal = Math.max(anchoTotal, largoSoles(e.total()));
            cantidadTotal += e.cantidad();
        }

        XSSFRow total = hoja.createRow(r);
        texto(total, 0, "TOTAL", estiloTotalTexto);
        entero(total, 1, cantidadTotal, estiloTotalEntero);
        // Mismo valor que "Egresos de caja" en la fila TOTAL de "Resultados" (calculado en SQL).
        soles(total, 2, rep.totales().egresos(), estiloTotalSoles);

        hoja.createFreezePane(0, filaEncabezado + 1);
        hoja.setColumnWidth(0, ancho(anchoMotivo + MARGEN_ANCHO));
        hoja.setColumnWidth(1, ancho(Math.max("Cantidad".length(), String.valueOf(cantidadTotal).length()) + MARGEN_ANCHO));
        hoja.setColumnWidth(2, ancho(anchoTotal + MARGEN_ANCHO));
    }

    private static void texto(XSSFRow row, int col, String valor, XSSFCellStyle estilo) {
        XSSFCell cell = row.createCell(col);
        cell.setCellValue(valor);
        cell.setCellStyle(estilo);
    }

    private static void soles(XSSFRow row, int col, BigDecimal valor, XSSFCellStyle estilo) {
        XSSFCell cell = row.createCell(col);
        // NUMERIC(12,2) → double: hasta 10^13 céntimos el redondeo a 2 decimales es exacto.
        cell.setCellValue(valor == null ? 0d : valor.doubleValue());
        cell.setCellStyle(estilo);
    }

    private static void entero(XSSFRow row, int col, long valor, XSSFCellStyle estilo) {
        XSSFCell cell = row.createCell(col);
        cell.setCellValue(valor);
        cell.setCellStyle(estilo);
    }

    /**
     * Largo en caracteres de un monto tal como lo muestra {@link #FORMATO_SOLES}
     * ("-S/ 12,345.67"). Aritmética de texto pura: sin fuentes ni AWT.
     */
    static int largoSoles(BigDecimal valor) {
        BigDecimal v = valor == null ? BigDecimal.ZERO : valor.setScale(2, java.math.RoundingMode.HALF_UP);
        String entero = v.abs().toBigInteger().toString();
        int separadores = (entero.length() - 1) / 3;
        return (v.signum() < 0 ? 1 : 0) + "S/ ".length() + entero.length() + separadores + ".00".length();
    }

    /** Ancho de columna en unidades de POI (1/256 de carácter). */
    private static int ancho(int caracteres) {
        return Math.min(caracteres, 80) * 256;
    }
}
