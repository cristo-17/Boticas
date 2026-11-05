package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.FilaDigemidResponse;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.RecetaRequest;
import com.botica.backend.dto.ReporteDigemidResponse;
import com.botica.backend.dto.SinConexionRequest;
import com.botica.backend.dto.TotalDigemidProductoResponse;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.exception.RangoFechasInvalidoException;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.util.FechaNegocio;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF-54 contra la base real (botica_test): el reporte DIGEMID lista solo las
 * líneas con requiere_receta congelado en true, con su lote, vencimiento y
 * receta; una línea sin lote (venta sin conexión) también; nada de otra
 * botica; y el rango es por día de Lima. La base de test tiene otros datos
 * de la misma botica: las aserciones filtran por los productos de cada test.
 * {@code @Transactional}: todo se revierte al terminar.
 */
@SpringBootTest
@Transactional
class DigemidIntegracionTest {

    @Autowired
    private DigemidService digemidService;
    @Autowired
    private VentaService ventaService;
    @Autowired
    private CajaDao cajaDao;
    @Autowired
    private ContextoOperacion contexto;
    @Autowired
    private FechaNegocio fechaNegocio;
    @Autowired
    private JdbcTemplate jdbc;

    private Long boticaId;
    private Long cajaId;
    private LocalDate hoy;
    private String tecnico;

    @BeforeEach
    void cajaAbierta() {
        boticaId = contexto.boticaId();
        hoy = fechaNegocio.hoy();
        if (cajaDao.buscarAbiertaDelUsuario(boticaId, contexto.usuarioId()).isEmpty()) {
            cajaDao.insertar(CajaDiaria.builder()
                    .boticaId(boticaId).usuarioId(contexto.usuarioId()).fecha(hoy).turno(contexto.turno())
                    .montoApertura(new BigDecimal("100.00")).horaApertura(fechaNegocio.ahora())
                    .build());
        }
        cajaId = cajaDao.buscarAbiertaDelUsuario(boticaId, contexto.usuarioId()).orElseThrow().getId();
        tecnico = jdbc.queryForObject("SELECT nombre FROM usuarios WHERE id = ?", String.class, contexto.usuarioId());
    }

    @Test
    void soloLasLineasConReceta_conSuLoteVencimientoRecetaYTecnico_yLosTotalesPorProducto() {
        long[] amox = producto("Amoxicilina DIGEMID QA", true);
        long[] vit = producto("Vitamina C DIGEMID QA", false);
        String s = sufijo();
        lote(amox[0], "A-LEJOS-" + s, hoy.plusDays(365), 100);
        lote(amox[0], "A-CERCA-" + s, hoy.plusDays(40), 50);
        lote(vit[0], "V-" + s, hoy.plusDays(300), 40);

        // FEFO: 60 de amoxicilina = 50 del lote que vence antes + 10 del otro. La vitamina no lleva receta.
        VentaResponse venta = ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(amox[0], amox[1], 60, "BUSQUEDA"),
                new ItemVentaRequest(vit[0], vit[1], 5, "BUSQUEDA")), "efectivo",
                new RecetaRequest("RX-77", "12345", "Dra. Ana Torres", hoy.minusDays(1))));
        ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(amox[0], amox[1], 2, "ESCANEO")), "yape",
                new RecetaRequest("RX-78", "54321", null, null)));

        ReporteDigemidResponse rep = digemidService.consultar(boticaId, hoy, hoy, 0, 100);
        List<FilaDigemidResponse> lineas = deProducto(rep, amox[0]);
        assertThat(rep.lineas().contenido()).noneMatch(f -> f.productoId().equals(vit[0]));
        assertThat(lineas).extracting(FilaDigemidResponse::cantidad).containsExactly(50, 10, 2);
        assertThat(lineas).extracting(FilaDigemidResponse::loteCodigo)
                .containsExactly("A-CERCA-" + s, "A-LEJOS-" + s, "A-CERCA-" + s);
        assertThat(lineas).extracting(FilaDigemidResponse::loteVencimiento)
                .containsExactly(hoy.plusDays(40), hoy.plusDays(365), hoy.plusDays(40));
        FilaDigemidResponse primera = lineas.getFirst();
        assertThat(primera.ventaId()).isEqualTo(venta.id());
        assertThat(primera.presentacion()).isEqualTo("Unidad");
        assertThat(primera.unidadNombre()).isEqualTo("cápsula");
        assertThat(primera.recetaNumero()).isEqualTo("RX-77");
        assertThat(primera.recetaCmp()).isEqualTo("12345");
        assertThat(primera.medicoNombre()).isEqualTo("Dra. Ana Torres");
        assertThat(primera.fechaEmision()).isEqualTo(hoy.minusDays(1));
        assertThat(primera.tecnico()).isEqualTo(tecnico);
        assertThat(lineas.get(2).medicoNombre()).isNull();
        assertThat(lineas.get(2).fechaEmision()).isNull();

        assertThat(totalDe(rep, amox[0])).satisfies(t -> {
            assertThat(t.recetas()).as("dos ventas, dos recetas (las dos líneas FEFO son una receta)").isEqualTo(2);
            assertThat(t.unidades()).isEqualTo(62);
            assertThat(t.producto()).isEqualTo("Amoxicilina DIGEMID QA");
        });
        assertThat(rep.totalesPorProducto()).noneMatch(t -> t.productoId().equals(vit[0]));
        assertThat(rep.nota()).isEqualTo(DigemidService.NOTA);
    }

    /** Congelado por línea: desmarcar el producto después no lo saca del reporte. */
    @Test
    void cambiarElCatalogoDespues_noSacaNiMeteLineas() {
        long[] p = producto("Clonazepam DIGEMID QA", true);
        lote(p[0], "C-" + sufijo(), hoy.plusDays(200), 20);
        ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(p[0], p[1], 3, "BUSQUEDA")), "efectivo", new RecetaRequest("RX-C", "999", null, null)));
        jdbc.update("UPDATE productos SET requiere_receta = false WHERE id = ?", p[0]);
        ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(p[0], p[1], 4, "BUSQUEDA")), "efectivo", null));

        assertThat(deProducto(digemidService.consultar(boticaId, hoy, hoy, 0, 100), p[0]))
                .extracting(FilaDigemidResponse::cantidad).containsExactly(3);
    }

    @Test
    void lineaSinLote_deUnaVentaSinConexion_seListaConLoteNulo() {
        long[] p = producto("Tramadol DIGEMID QA", true);
        String codigo = "T-" + sufijo();
        lote(p[0], codigo, hoy.plusDays(90), 3);

        // Sin conexión con 5 pedidos y 3 en stock: 3 del lote y 2 sin lote (revisión STOCK_INSUFICIENTE).
        ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(p[0], p[1], 5, "BUSQUEDA", new BigDecimal("2.00"))), "efectivo",
                new RecetaRequest("RX-SC", "777", null, null),
                new SinConexionRequest(cajaId, OffsetDateTime.now())));

        List<FilaDigemidResponse> lineas = deProducto(digemidService.consultar(boticaId, hoy, hoy, 0, 100), p[0]);
        assertThat(lineas).hasSize(2);
        assertThat(lineas.get(0).loteCodigo()).isEqualTo(codigo);
        assertThat(lineas.get(0).cantidad()).isEqualTo(3);
        assertThat(lineas.get(1).loteId()).isNull();
        assertThat(lineas.get(1).loteCodigo()).isNull();
        assertThat(lineas.get(1).loteVencimiento()).isNull();
        assertThat(lineas.get(1).cantidad()).isEqualTo(2);
        assertThat(lineas.get(1).recetaNumero()).isEqualTo("RX-SC");
    }

    @Test
    void otraBotica_noVeLasLineasDeEsta() {
        long[] p = producto("Ciprofloxacino DIGEMID QA", true);
        lote(p[0], "CI-" + sufijo(), hoy.plusDays(200), 10);
        ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(p[0], p[1], 1, "BUSQUEDA")), "efectivo", new RecetaRequest("RX-AIS", "111", null, null)));
        Long otraBotica = jdbc.queryForObject(
                "INSERT INTO boticas (nombre, ruc, direccion) VALUES ('Botica Otra DIGEMID', ?, 'Av. Otra 1') RETURNING id",
                Long.class, String.valueOf(10000000000L + (System.nanoTime() % 89999999999L)));

        assertThat(deProducto(digemidService.consultar(boticaId, hoy, hoy, 0, 100), p[0])).hasSize(1);
        ReporteDigemidResponse ajeno = digemidService.consultar(otraBotica, hoy, hoy, 0, 100);
        assertThat(ajeno.lineas().contenido()).isEmpty();
        assertThat(ajeno.totalesPorProducto()).isEmpty();
        assertThat(ajeno.totales().recetas()).isZero();
        assertThat(ajeno.totales().unidades()).isZero();
    }

    /** Día de Lima: una venta a las 23:30 de ayer (Lima) es de ayer, no de hoy. Mismas reglas de rango que Resultados. */
    @Test
    void rango_porDiaDeLima_yValidaciones() {
        long[] p = producto("Metformina DIGEMID QA", true);
        lote(p[0], "M-" + sufijo(), hoy.plusDays(300), 50);
        VentaResponse ayer = ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(p[0], p[1], 2, "BUSQUEDA")), "efectivo", new RecetaRequest("RX-AY", "222", null, null)));
        VentaResponse deHoy = ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(p[0], p[1], 3, "BUSQUEDA")), "efectivo", new RecetaRequest("RX-HO", "333", null, null)));
        jdbc.update("UPDATE ventas SET fecha = ? WHERE id = ?",
                hoy.minusDays(1).atTime(23, 30).atZone(FechaNegocio.ZONA_LIMA).toOffsetDateTime(), ayer.id());

        assertThat(deProducto(digemidService.consultar(boticaId, hoy, hoy, 0, 100), p[0]))
                .extracting(FilaDigemidResponse::ventaId).containsExactly(deHoy.id());
        assertThat(deProducto(digemidService.consultar(boticaId, hoy.minusDays(1), hoy.minusDays(1), 0, 100), p[0]))
                .extracting(FilaDigemidResponse::ventaId).containsExactly(ayer.id());
        assertThat(deProducto(digemidService.consultar(boticaId, hoy.minusDays(1), hoy, 0, 100), p[0]))
                .extracting(FilaDigemidResponse::ventaId).containsExactly(ayer.id(), deHoy.id());

        assertThatThrownBy(() -> digemidService.consultar(boticaId, hoy, hoy.minusDays(1), 0, 50))
                .isInstanceOf(RangoFechasInvalidoException.class);
        assertThatThrownBy(() -> digemidService.consultar(boticaId, hoy.minusYears(1).minusDays(1), hoy, 0, 50))
                .isInstanceOf(RangoFechasInvalidoException.class);
        assertThatThrownBy(() -> digemidService.generarExcel(boticaId, null, hoy))
                .isInstanceOf(RangoFechasInvalidoException.class);
    }

    @Test
    void paginado_losTotalesSonDeTodoElRango() {
        long[] p = producto("Losartán DIGEMID QA", true);
        lote(p[0], "LO-" + sufijo(), hoy.plusDays(300), 50);
        for (int i = 1; i <= 3; i++) {
            ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                    new ItemVentaRequest(p[0], p[1], i, "BUSQUEDA")), "efectivo", new RecetaRequest("RX-P" + i, "44" + i, null, null)));
        }
        ReporteDigemidResponse todo = digemidService.consultar(boticaId, hoy, hoy, 0, 100);
        ReporteDigemidResponse pagina = digemidService.consultar(boticaId, hoy, hoy, 1, 1);
        assertThat(pagina.lineas().contenido()).singleElement()
                .isEqualTo(todo.lineas().contenido().get(1));
        assertThat(pagina.lineas().totalElementos()).isEqualTo(todo.totales().lineas());
        assertThat(pagina.totales()).isEqualTo(todo.totales());
        assertThat(totalDe(pagina, p[0]).unidades()).isEqualTo(6);
    }

    @Test
    void elExcel_traeTodasLasLineas_elRotuloDeSinLote_yLosTotales() throws Exception {
        long[] p = producto("Azitromicina DIGEMID QA", true);
        String codigo = "AZ-" + sufijo();
        lote(p[0], codigo, hoy.plusDays(25), 1);
        ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(p[0], p[1], 3, "BUSQUEDA", new BigDecimal("2.00"))), "efectivo",
                new RecetaRequest("RX-XL", "555", "Dr. Luis Paz", hoy),
                new SinConexionRequest(cajaId, OffsetDateTime.now())));
        ReporteDigemidResponse rep = digemidService.consultar(boticaId, hoy, hoy, 0, 100);

        byte[] xlsx = digemidService.generarExcel(boticaId, hoy, hoy);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            XSSFSheet hoja = wb.getSheet(DigemidExcel.HOJA);
            assertThat(hoja.getRow(4).getCell(0).getStringCellValue()).isEqualTo(DigemidService.NOTA);
            assertThat(hoja.getRow(DigemidExcel.FILA_ENCABEZADO).getCell(5).getStringCellValue()).isEqualTo("Lote");

            int filaLote = -1;
            int filaSinLote = -1;
            int primera = DigemidExcel.FILA_ENCABEZADO + 1;
            for (int r = primera; r < primera + rep.totales().lineas(); r++) {
                if (!"Azitromicina DIGEMID QA".equals(hoja.getRow(r).getCell(2).getStringCellValue())) {
                    continue;
                }
                if (codigo.equals(hoja.getRow(r).getCell(5).getStringCellValue())) {
                    filaLote = r;
                } else {
                    filaSinLote = r;
                }
            }
            assertThat(filaLote).as("todas las líneas, sin paginar").isPositive();
            assertThat(hoja.getRow(filaLote).getCell(4).getNumericCellValue()).isEqualTo(1d);
            assertThat(hoja.getRow(filaLote).getCell(6).getStringCellValue())
                    .isEqualTo(String.format("%02d/%02d/%d", hoy.plusDays(25).getDayOfMonth(),
                            hoy.plusDays(25).getMonthValue(), hoy.plusDays(25).getYear()));
            assertThat(hoja.getRow(filaLote).getCell(7).getStringCellValue()).isEqualTo("RX-XL");
            assertThat(hoja.getRow(filaLote).getCell(9).getStringCellValue()).isEqualTo("Dr. Luis Paz");
            assertThat(hoja.getRow(filaLote).getCell(11).getStringCellValue()).isEqualTo(tecnico);
            assertThat(hoja.getRow(filaSinLote).getCell(5).getStringCellValue()).isEqualTo("Sin lote (en revisión)");
            assertThat(hoja.getRow(filaSinLote).getCell(4).getNumericCellValue()).isEqualTo(2d);

            int ultima = hoja.getLastRowNum();
            assertThat(hoja.getRow(ultima).getCell(0).getStringCellValue()).isEqualTo("TOTAL");
            assertThat(hoja.getRow(ultima).getCell(1).getNumericCellValue()).isEqualTo((double) rep.totales().recetas());
            assertThat(hoja.getRow(ultima).getCell(2).getNumericCellValue()).isEqualTo((double) rep.totales().unidades());
            boolean totalProducto = false;
            for (int r = primera + (int) rep.totales().lineas(); r < ultima; r++) {
                var row = hoja.getRow(r);
                if (row != null && row.getCell(0) != null && "Azitromicina DIGEMID QA".equals(row.getCell(0).getStringCellValue())) {
                    assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(1d);
                    assertThat(row.getCell(2).getNumericCellValue()).isEqualTo(3d);
                    totalProducto = true;
                }
            }
            assertThat(totalProducto).as("total del producto al pie").isTrue();
        }
    }

    private static List<FilaDigemidResponse> deProducto(ReporteDigemidResponse rep, long productoId) {
        return rep.lineas().contenido().stream().filter(f -> f.productoId() == productoId).toList();
    }

    private static TotalDigemidProductoResponse totalDe(ReporteDigemidResponse rep, long productoId) {
        return rep.totalesPorProducto().stream().filter(t -> t.productoId() == productoId).findFirst().orElseThrow();
    }

    /** productoId y presentacionId ("Unidad", factor 1, S/ 2.00), unidad "cápsula". */
    private long[] producto(String nombre, boolean requiereReceta) {
        String codigo = "QA-D-" + sufijo();
        Long productoId = jdbc.queryForObject("INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre, requiere_receta) " +
                "VALUES (?, ?, 'Otros', ?, 'cápsula', ?) RETURNING id", Long.class, boticaId, nombre, codigo, requiereReceta);
        Long presentacionId = jdbc.queryForObject("INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) " +
                "VALUES (?, ?, 'Unidad', 1, 2.00) RETURNING id", Long.class, boticaId, productoId);
        return new long[]{productoId, presentacionId};
    }

    private void lote(long productoId, String codigo, LocalDate vencimiento, int stock) {
        jdbc.update("INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                "VALUES (?, ?, ?, ?, ?, 1.00)", boticaId, productoId, codigo, vencimiento, stock);
    }

    private static String sufijo() {
        return Long.toString(System.nanoTime() % 1_000_000_000L, 36);
    }
}
