package com.botica.backend.service;

import com.botica.backend.dto.FilaDigemidResponse;
import com.botica.backend.dto.ReporteDigemidResponse;
import com.botica.backend.dto.TotalDigemidProductoResponse;
import com.botica.backend.util.FechaNegocio;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * .xlsx del reporte DIGEMID (RF-54), con el criterio de {@link KardexExcel}:
 * valores del servidor (sin fórmulas), anchos por largo de texto, sin
 * {@code java.awt} ni {@code autoSizeColumn}. Una hoja: líneas, y debajo los
 * totales por producto y el total general.
 */
final class DigemidExcel {

    static final String HOJA = "DIGEMID";
    static final int FILA_ENCABEZADO = 6;
    static final int MARGEN_ANCHO = 4;
    static final String SIN_LOTE = "Sin lote (en revisión)";
    static final String SIN_RECETA = "Sin receta (en revisión)";

    static final String[] ENCABEZADOS = {
            "Fecha y hora", "Venta", "Producto", "Presentación", "Cantidad (unidades)", "Lote", "Vencimiento",
            "N.º receta", "CMP", "Médico", "Emisión", "Técnico"
    };
    static final String[] ENCABEZADOS_TOTALES = {"Producto", "Recetas", "Unidades dispensadas"};

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final byte[] VERDE_ENCABEZADO = {(byte) 0xD9, (byte) 0xEF, (byte) 0xE3};
    private static final byte[] GRIS_TOTAL = {(byte) 0xF0, (byte) 0xF2, (byte) 0xF4};

    private final XSSFWorkbook wb = new XSSFWorkbook();
    private final XSSFCellStyle estiloTitulo;
    private final XSSFCellStyle estiloMeta;
    private final XSSFCellStyle estiloNota;
    private final XSSFCellStyle estiloEncabezado;
    private final XSSFCellStyle estiloTexto;
    private final XSSFCellStyle estiloEntero;
    private final XSSFCellStyle estiloTotalTexto;
    private final XSSFCellStyle estiloTotalEntero;

    private DigemidExcel() {
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

        estiloTexto = wb.createCellStyle();
        estiloEntero = wb.createCellStyle();
        estiloEntero.setDataFormat(formatoEntero);

        estiloTotalTexto = estiloTotal(negrita);
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

    /** @param generadoEn instante de generación ya en zona de Lima ({@code FechaNegocio.ahora()}). */
    static byte[] generar(String nombreBotica, ReporteDigemidResponse reporte, OffsetDateTime generadoEn) {
        DigemidExcel excel = new DigemidExcel();
        try (XSSFWorkbook wb = excel.wb) {
            wb.getProperties().getCoreProperties().setTitle("Dispensación con receta");
            wb.getProperties().getCoreProperties().setCreator("BoticaSys");
            excel.hoja(nombreBotica, reporte, generadoEn);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel del reporte DIGEMID", e);
        }
    }

    private void hoja(String nombreBotica, ReporteDigemidResponse rep, OffsetDateTime generadoEn) {
        XSSFSheet hoja = wb.createSheet(HOJA);
        texto(hoja.createRow(0), 0, nombreBotica, estiloTitulo);
        texto(hoja.createRow(1), 0, "Dispensación con receta (DIGEMID)", estiloMeta);
        texto(hoja.createRow(2), 0, "Del " + FMT_FECHA.format(rep.desde()) + " al " + FMT_FECHA.format(rep.hasta())
                + " · fechas y horas de Lima · cantidades en unidades", estiloMeta);
        texto(hoja.createRow(3), 0, "Generado: " + FMT_FECHA_HORA.format(generadoEn) + " (hora de Lima)", estiloMeta);
        texto(hoja.createRow(4), 0, rep.nota(), estiloNota);

        XSSFRow enc = hoja.createRow(FILA_ENCABEZADO);
        int[] largos = new int[ENCABEZADOS.length];
        for (int c = 0; c < ENCABEZADOS.length; c++) {
            texto(enc, c, ENCABEZADOS[c], estiloEncabezado);
            largos[c] = ENCABEZADOS[c].length();
        }

        int r = FILA_ENCABEZADO + 1;
        for (FilaDigemidResponse f : rep.lineas().contenido()) {
            XSSFRow row = hoja.createRow(r++);
            String[] textos = {
                    FMT_FECHA_HORA.format(f.fecha().atZoneSameInstant(FechaNegocio.ZONA_LIMA)),
                    "#" + f.ventaId(),
                    f.producto(),
                    f.presentacion(),
                    null,
                    f.loteId() == null ? SIN_LOTE : f.loteCodigo(),
                    fecha(f.loteVencimiento()),
                    f.recetaNumero() == null ? SIN_RECETA : f.recetaNumero(),
                    nulo(f.recetaCmp()),
                    nulo(f.medicoNombre()),
                    fecha(f.fechaEmision()),
                    nulo(f.tecnico())
            };
            for (int c = 0; c < textos.length; c++) {
                if (textos[c] == null) {
                    continue;
                }
                texto(row, c, textos[c], estiloTexto);
                largos[c] = Math.max(largos[c], textos[c].length());
            }
            entero(row, 4, f.cantidad(), estiloEntero);
        }
        int ultimaFilaDatos = r - 1;

        // Totales al pie: por producto y general (del servidor, no fórmulas).
        r++;
        XSSFRow encTot = hoja.createRow(r++);
        for (int c = 0; c < ENCABEZADOS_TOTALES.length; c++) {
            texto(encTot, c, ENCABEZADOS_TOTALES[c], estiloEncabezado);
        }
        for (TotalDigemidProductoResponse t : rep.totalesPorProducto()) {
            XSSFRow row = hoja.createRow(r++);
            texto(row, 0, t.producto(), estiloTexto);
            entero(row, 1, t.recetas(), estiloEntero);
            entero(row, 2, t.unidades(), estiloEntero);
            largos[0] = Math.max(largos[0], t.producto().length());
        }
        XSSFRow total = hoja.createRow(r);
        texto(total, 0, "TOTAL", estiloTotalTexto);
        entero(total, 1, rep.totales().recetas(), estiloTotalEntero);
        entero(total, 2, rep.totales().unidades(), estiloTotalEntero);

        hoja.createFreezePane(0, FILA_ENCABEZADO + 1);
        if (ultimaFilaDatos > FILA_ENCABEZADO) {
            hoja.setAutoFilter(new CellRangeAddress(FILA_ENCABEZADO, ultimaFilaDatos, 0, ENCABEZADOS.length - 1));
        }
        for (int c = 0; c < ENCABEZADOS.length; c++) {
            hoja.setColumnWidth(c, Math.min(Math.max(largos[c], c < 3 ? ENCABEZADOS_TOTALES[c].length() : 0)
                    + MARGEN_ANCHO, 60) * 256);
        }
        hoja.getPrintSetup().setLandscape(true);
        hoja.setFitToPage(true);
        hoja.getPrintSetup().setFitWidth((short) 1);
        hoja.getPrintSetup().setFitHeight((short) 0);
    }

    private static String fecha(LocalDate d) {
        return d == null ? "" : FMT_FECHA.format(d);
    }

    private static String nulo(String s) {
        return s == null ? "" : s;
    }

    private static void texto(XSSFRow row, int col, String valor, XSSFCellStyle estilo) {
        XSSFCell cell = row.createCell(col);
        cell.setCellValue(valor);
        cell.setCellStyle(estilo);
    }

    private static void entero(XSSFRow row, int col, long valor, XSSFCellStyle estilo) {
        XSSFCell cell = row.createCell(col);
        cell.setCellValue(valor);
        cell.setCellStyle(estilo);
    }
}
