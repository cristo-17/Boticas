package com.botica.backend.service;

import com.botica.backend.dto.FilaKardexResponse;
import com.botica.backend.dto.KardexResponse;
import com.botica.backend.dto.ResumenKardexResponse;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Arma el .xlsx del Kardex de un producto (Tarea 20) con Apache POI, con el
 * mismo criterio que {@link ReporteExcel}: valores calculados por el servidor
 * (saldo de SQL, nunca fórmulas), anchos por largo de texto y nada de
 * {@code java.awt} ni {@code autoSizeColumn} (500 en Render, ver ReporteExcel).
 */
final class KardexExcel {

    static final String HOJA = "Kardex";
    static final int FILA_ENCABEZADO = 6;
    static final int MARGEN_ANCHO = 4;

    static final String[] ENCABEZADOS = {
            "Fecha y hora", "Movimiento", "Lote", "Referencia", "Usuario", "Entrada", "Salida", "Saldo"
    };

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
    private final XSSFCellStyle estiloSaldoAnterior;
    private final XSSFCellStyle estiloTotalTexto;
    private final XSSFCellStyle estiloTotalEntero;

    private KardexExcel() {
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

        estiloSaldoAnterior = wb.createCellStyle();
        estiloSaldoAnterior.setFont(fuenteNota);

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
    static byte[] generar(String nombreBotica, KardexResponse kardex, OffsetDateTime generadoEn) {
        KardexExcel excel = new KardexExcel();
        try (XSSFWorkbook wb = excel.wb) {
            wb.getProperties().getCoreProperties().setTitle("Kardex · " + kardex.productoNombre());
            wb.getProperties().getCoreProperties().setCreator("BoticaSys");
            excel.hoja(nombreBotica, kardex, generadoEn);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel del Kardex", e);
        }
    }

    /** Mismo texto que la pantalla Kardex. */
    static String etiquetaTipo(FilaKardexResponse f) {
        return switch (f.tipo()) {
            case "SALDO_ANTERIOR" -> "Saldo anterior";
            case "ENTRADA" -> f.entradaInicial() ? "Entrada inicial (registrada por la migración)" : "Entrada (lote)";
            case "SALIDA_RECETA" -> "Salida con receta";
            case "SALIDA_LIBRE" -> "Salida libre";
            case "MERMA" -> "Merma";
            default -> f.tipo();
        };
    }

    static String referencia(FilaKardexResponse f) {
        if (f.ventaId() != null) {
            return "Venta #" + f.ventaId() + (f.recetaNumero() != null ? " · Receta " + f.recetaNumero() : "");
        }
        if (f.mermaId() != null) {
            return "Merma #" + f.mermaId();
        }
        return "";
    }

    static String textoRango(KardexResponse k) {
        if (k.desde() == null && k.hasta() == null) {
            return "Toda la historia";
        }
        String desde = k.desde() == null ? "el inicio" : FMT_FECHA.format(k.desde());
        String hasta = k.hasta() == null ? "hoy" : FMT_FECHA.format(k.hasta());
        return "Del " + desde + " al " + hasta;
    }

    private void hoja(String nombreBotica, KardexResponse k, OffsetDateTime generadoEn) {
        XSSFSheet hoja = wb.createSheet(HOJA);
        texto(hoja.createRow(0), 0, nombreBotica, estiloTitulo);
        texto(hoja.createRow(1), 0, "Kardex · " + k.productoNombre()
                + (k.requiereReceta() ? " (requiere receta)" : "")
                + (k.activo() ? "" : " (desactivado)"), estiloMeta);
        texto(hoja.createRow(2), 0, textoRango(k) + " · unidades: " + k.unidadNombre(), estiloMeta);
        texto(hoja.createRow(3), 0, "Generado: " + FMT_FECHA_HORA.format(generadoEn) + " (hora de Lima)", estiloMeta);
        texto(hoja.createRow(4), 0,
                "Saldo calculado en el servidor desde los movimientos de stock. Stock actual de los lotes: "
                        + k.resumen().stockActual() + ".", estiloNota);

        XSSFRow enc = hoja.createRow(FILA_ENCABEZADO);
        int[] largos = new int[ENCABEZADOS.length];
        for (int c = 0; c < ENCABEZADOS.length; c++) {
            texto(enc, c, ENCABEZADOS[c], estiloEncabezado);
            largos[c] = ENCABEZADOS[c].length();
        }

        int r = FILA_ENCABEZADO + 1;
        for (FilaKardexResponse f : k.movimientos().contenido()) {
            XSSFRow row = hoja.createRow(r++);
            boolean saldoAnterior = "SALDO_ANTERIOR".equals(f.tipo());
            String[] textos = {
                    f.fecha() == null ? "" : FMT_FECHA_HORA.format(f.fecha().atZoneSameInstant(FechaNegocio.ZONA_LIMA)),
                    etiquetaTipo(f),
                    f.loteCodigo() == null ? "" : f.loteCodigo(),
                    referencia(f),
                    f.usuario() == null ? "" : f.usuario()
            };
            for (int c = 0; c < textos.length; c++) {
                texto(row, c, textos[c], saldoAnterior ? estiloSaldoAnterior : estiloTexto);
                largos[c] = Math.max(largos[c], textos[c].length());
            }
            if (f.cantidad() != null && f.cantidad() > 0) {
                entero(row, 5, f.cantidad(), estiloEntero);
            }
            if (f.cantidad() != null && f.cantidad() < 0) {
                entero(row, 6, -f.cantidad(), estiloEntero);
            }
            entero(row, 7, f.saldo(), estiloEntero);
            largos[7] = Math.max(largos[7], largoEntero(f.saldo()));
        }
        int ultimaFilaDatos = r - 1;

        ResumenKardexResponse res = k.resumen();
        XSSFRow total = hoja.createRow(r);
        texto(total, 0, "TOTAL", estiloTotalTexto);
        for (int c = 1; c < 5; c++) {
            texto(total, c, "", estiloTotalTexto);
        }
        entero(total, 5, res.entradas(), estiloTotalEntero);
        entero(total, 6, res.salidas(), estiloTotalEntero);
        entero(total, 7, res.saldoFinal(), estiloTotalEntero);
        largos[5] = Math.max(largos[5], largoEntero(res.entradas()));
        largos[6] = Math.max(largos[6], largoEntero(res.salidas()));
        largos[7] = Math.max(largos[7], largoEntero(res.saldoFinal()));

        hoja.createFreezePane(0, FILA_ENCABEZADO + 1);
        if (ultimaFilaDatos > FILA_ENCABEZADO) {
            hoja.setAutoFilter(new CellRangeAddress(FILA_ENCABEZADO, ultimaFilaDatos, 0, ENCABEZADOS.length - 1));
        }
        for (int c = 0; c < ENCABEZADOS.length; c++) {
            hoja.setColumnWidth(c, Math.min(largos[c] + MARGEN_ANCHO, 80) * 256);
        }
        hoja.getPrintSetup().setLandscape(true);
        hoja.setFitToPage(true);
        hoja.getPrintSetup().setFitWidth((short) 1);
        hoja.getPrintSetup().setFitHeight((short) 0);
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

    /** Largo de "12,345" (formato #,##0): aritmética de texto, sin fuentes. */
    static int largoEntero(long valor) {
        String digitos = Long.toString(Math.abs(valor));
        return (valor < 0 ? 1 : 0) + digitos.length() + (digitos.length() - 1) / 3;
    }
}
