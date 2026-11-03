package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dao.KardexDao;
import com.botica.backend.dto.FilaKardexResponse;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.KardexResponse;
import com.botica.backend.dto.LoteResponse;
import com.botica.backend.dto.NuevaMermaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.NuevoLoteRequest;
import com.botica.backend.dto.PresentacionRequest;
import com.botica.backend.dto.ProductoRequest;
import com.botica.backend.dto.RecetaRequest;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 20, Bloque B — el Kardex contra la base real (botica_test).
 *
 * <p>INVARIANTE: el saldo final del Kardex de un producto es exactamente la
 * suma del stock de sus lotes, y el saldo nunca queda negativo en ningún
 * punto. Si no coincide, hay un movimiento que no se está registrando.</p>
 *
 * <p>@Transactional: todo se revierte al terminar. Dentro de una transacción
 * now() es el mismo instante para todo, así que el orden del flujo completo
 * lo da el id; el test de rango inserta movimientos con fecha explícita.</p>
 */
@SpringBootTest
@Transactional
class KardexIntegracionTest {

    @Autowired
    private KardexService kardexService;
    @Autowired
    private KardexDao kardexDao;
    @Autowired
    private LoteService loteService;
    @Autowired
    private VentaService ventaService;
    @Autowired
    private MermaService mermaService;
    @Autowired
    private CatalogoProductoService catalogoService;
    @Autowired
    private CajaDao cajaDao;
    @Autowired
    private ContextoOperacion contexto;
    @Autowired
    private FechaNegocio fechaNegocio;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long boticaId;
    private LocalDate hoy;

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
    }

    @Test
    void invariante_flujoCompleto_lotesVentasConYSinRecetaYMerma() {
        long[] amox = producto("Amoxicilina QA", true);
        long[] vit = producto("Vitamina C QA", false);
        String s = sufijo();
        LoteResponse a1 = loteService.registrar(new NuevoLoteRequest(amox[0], "A1-" + s, hoy.plusDays(365), 100, null, new BigDecimal("1.00")));
        LoteResponse a2 = loteService.registrar(new NuevoLoteRequest(amox[0], "A2-" + s, hoy.plusDays(200), 50, null, new BigDecimal("1.00")));
        loteService.registrar(new NuevoLoteRequest(vit[0], "V1-" + s, hoy.plusDays(300), 40, null, new BigDecimal("0.50")));

        // Venta con receta que lleva también una vitamina: la línea de vitamina es SALIDA_LIBRE (por línea, no por venta).
        // FEFO: 60 de amoxicilina = 50 de A2 (vence antes) + 10 de A1.
        ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(amox[0], amox[1], 60, "BUSQUEDA"),
                new ItemVentaRequest(vit[0], vit[1], 5, "BUSQUEDA")), "efectivo",
                new RecetaRequest(" R-001 ", "12345", "Dra. Ana Torres", hoy)));
        ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(vit[0], vit[1], 3, "ESCANEO")), "yape", null));
        mermaService.registrar(new NuevaMermaRequest(a1.id(), 4, "Rotura", null));

        KardexResponse kAmox = kardexService.consultar(amox[0], null, null, 0, 50);
        assertThat(kAmox.movimientos().contenido()).extracting(FilaKardexResponse::tipo)
                .containsExactly("ENTRADA", "ENTRADA", "SALIDA_RECETA", "SALIDA_RECETA", "MERMA");
        assertThat(kAmox.movimientos().contenido()).extracting(FilaKardexResponse::cantidad)
                .containsExactly(100, 50, -50, -10, -4);
        assertThat(kAmox.movimientos().contenido()).extracting(FilaKardexResponse::saldo)
                .containsExactly(100L, 150L, 100L, 90L, 86L);
        assertThat(kAmox.movimientos().contenido().get(2).recetaNumero()).isEqualTo("R-001");
        assertThat(kAmox.movimientos().contenido().get(2).loteId()).isEqualTo(a2.id());
        assertThat(kAmox.movimientos().contenido()).noneMatch(FilaKardexResponse::entradaInicial);
        assertInvariante(amox[0], kAmox);

        KardexResponse kVit = kardexService.consultar(vit[0], null, null, 0, 50);
        assertThat(kVit.movimientos().contenido()).extracting(FilaKardexResponse::tipo)
                .containsExactly("ENTRADA", "SALIDA_LIBRE", "SALIDA_LIBRE");
        assertThat(kVit.movimientos().contenido().get(1).recetaNumero())
                .as("la vitamina salió en una venta con receta, pero su línea no la necesitaba").isNull();
        assertInvariante(vit[0], kVit);
    }

    /**
     * "Con receta" va congelado por línea (venta_detalle.requiere_receta), como el
     * precio y el costo (RF-19): cambiar el catálogo no reescribe lo dispensado.
     * El cambio de catálogo pasa por el Service real (PUT del catálogo).
     */
    @Test
    void cambiarRequiereRecetaEnElCatalogo_noReescribeLasSalidasAnteriores() {
        long[] p = producto("Clonazepam congelado QA", true);
        loteService.registrar(new NuevoLoteRequest(p[0], "C1-" + sufijo(), hoy.plusDays(365), 50, null, new BigDecimal("1.00")));
        ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(p[0], p[1], 5, "BUSQUEDA")), "efectivo",
                new RecetaRequest("R-CONG", "54321", null, hoy)));

        // Se desmarca: la venta siguiente ya no pide receta y sale libre.
        catalogoService.actualizar(p[0], requestCatalogo(p[0], false));
        ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(p[0], p[1], 3, "BUSQUEDA")), "efectivo", null));

        List<FilaKardexResponse> filas = kardexService.consultar(p[0], null, null, 0, 50).movimientos().contenido();
        assertThat(filas).extracting(FilaKardexResponse::tipo)
                .as("la salida con receta anterior sigue siéndolo aunque el producto ya no la requiera")
                .containsExactly("ENTRADA", "SALIDA_RECETA", "SALIDA_LIBRE");
        assertThat(filas.get(1).recetaNumero()).isEqualTo("R-CONG");
        assertThat(filas.get(2).recetaNumero()).isNull();
        assertThat(jdbcTemplate.queryForList(
                "SELECT requiere_receta FROM venta_detalle WHERE producto_id = ? ORDER BY id", Boolean.class, p[0]))
                .containsExactly(true, false);

        // Y al revés: marcarlo de nuevo no convierte en "con receta" la salida libre.
        catalogoService.actualizar(p[0], requestCatalogo(p[0], true));
        assertThat(kardexService.consultar(p[0], null, null, 0, 50).movimientos().contenido())
                .extracting(FilaKardexResponse::tipo)
                .containsExactly("ENTRADA", "SALIDA_RECETA", "SALIDA_LIBRE");
        assertInvariante(p[0], kardexService.consultar(p[0], null, null, 0, 50));
    }

    @Test
    void conDesde_laPrimeraFilaEsElSaldoAnterior_yElAcumuladoSigueDesdeAhi() {
        long[] p = producto("Ibuprofeno rango QA", false);
        long l1 = lote(p[0], 75);   // 100 − 20 − 5
        long l2 = lote(p[0], 20);   // 30 − 10
        movimiento(l1, "INGRESO", 100, hoy.minusDays(10));
        movimiento(l1, "VENTA", -20, hoy.minusDays(7));
        movimiento(l1, "MERMA", -5, hoy.minusDays(5));
        movimiento(l2, "INGRESO", 30, hoy.minusDays(3));
        movimiento(l2, "VENTA", -10, hoy.minusDays(1));

        KardexResponse k = kardexService.consultar(p[0], hoy.minusDays(6), hoy.minusDays(2), 0, 50);
        List<FilaKardexResponse> filas = k.movimientos().contenido();
        assertThat(filas).extracting(FilaKardexResponse::tipo).containsExactly("SALDO_ANTERIOR", "MERMA", "ENTRADA");
        assertThat(filas).extracting(FilaKardexResponse::saldo).containsExactly(80L, 75L, 105L);
        assertThat(filas.get(0).cantidad()).isNull();
        assertThat(filas.get(0).fecha().toInstant())
                .isEqualTo(hoy.minusDays(6).atStartOfDay(FechaNegocio.ZONA_LIMA).toInstant());
        assertThat(k.resumen().saldoInicial()).isEqualTo(80);
        assertThat(k.resumen().entradas()).isEqualTo(30);
        assertThat(k.resumen().salidas()).isEqualTo(5);
        assertThat(k.resumen().saldoFinal()).isEqualTo(105);
        assertThat(k.resumen().movimientos()).isEqualTo(2);
        assertThat(k.movimientos().totalElementos()).as("2 movimientos + la fila de saldo anterior").isEqualTo(3);

        // La ventana se calcula antes de paginar: la página 2 de tamaño 1 trae el saldo correcto.
        KardexResponse pagina2 = kardexService.consultar(p[0], hoy.minusDays(6), hoy.minusDays(2), 1, 1);
        assertThat(pagina2.movimientos().contenido()).singleElement()
                .satisfies(f -> {
                    assertThat(f.tipo()).isEqualTo("MERMA");
                    assertThat(f.saldo()).isEqualTo(75L);
                });

        // Sin "hasta": llega a hoy y cuadra con el stock.
        KardexResponse hastaHoy = kardexService.consultar(p[0], hoy.minusDays(6), null, 0, 50);
        assertThat(hastaHoy.resumen().saldoFinal()).isEqualTo(95);
        assertInvariante(p[0], kardexService.consultar(p[0], null, null, 0, 50));

        // "desde" posterior a todo: solo el saldo anterior, igual al stock.
        KardexResponse futuro = kardexService.consultar(p[0], hoy.plusDays(1), null, 0, 50);
        assertThat(futuro.movimientos().contenido()).singleElement()
                .satisfies(f -> assertThat(f.saldo()).isEqualTo(95L));
    }

    @Test
    void elExcelTraeTodasLasFilasSinPaginar() throws Exception {
        long[] p = producto("Excel QA", false);
        long l = lote(p[0], 7);
        movimiento(l, "INGRESO", 10, hoy.minusDays(3));
        movimiento(l, "VENTA", -3, hoy.minusDays(2));

        byte[] xlsx = kardexService.generarExcel(p[0], hoy.minusDays(2), null);
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            var hoja = wb.getSheet(KardexExcel.HOJA);
            int primera = KardexExcel.FILA_ENCABEZADO + 1;
            assertThat(hoja.getRow(primera).getCell(1).getStringCellValue()).isEqualTo("Saldo anterior");
            assertThat(hoja.getRow(primera).getCell(7).getNumericCellValue()).isEqualTo(10d);
            assertThat(hoja.getRow(primera + 1).getCell(1).getStringCellValue()).isEqualTo("Salida libre");
            assertThat(hoja.getRow(primera + 1).getCell(6).getNumericCellValue()).isEqualTo(3d);
            assertThat(hoja.getRow(primera + 2).getCell(0).getStringCellValue()).isEqualTo("TOTAL");
            assertThat(hoja.getRow(primera + 2).getCell(7).getNumericCellValue()).isEqualTo(7d);
        }
    }

    /**
     * V10 completó una entrada por lote existente (creado_por NULL). En la base de
     * test eso cubre los lotes del seed de dev: cada uno cuadra con su stock y su
     * entrada inicial queda antes que todos sus otros movimientos.
     */
    @Test
    void entradasDeLaMigracion_cuadranConElStock_yVanAntesQueSusSalidas() {
        Long entradasIniciales = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM movimientos_stock WHERE tipo = 'INGRESO' AND creado_por IS NULL", Long.class);
        assertThat(entradasIniciales).as("el seed de dev tiene lotes anteriores a V10").isPositive();

        List<Long> descuadrados = jdbcTemplate.queryForList("""
                SELECT l.id FROM lotes l
                WHERE EXISTS (SELECT 1 FROM movimientos_stock e
                              WHERE e.lote_id = l.id AND e.tipo = 'INGRESO' AND e.creado_por IS NULL)
                  AND l.stock <> (SELECT SUM(m.cantidad) FROM movimientos_stock m WHERE m.lote_id = l.id)
                """, Long.class);
        assertThat(descuadrados).isEmpty();

        Long antesDeSuEntrada = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM movimientos_stock m
                JOIN movimientos_stock e ON e.lote_id = m.lote_id AND e.tipo = 'INGRESO' AND e.creado_por IS NULL
                WHERE m.id <> e.id AND m.creado_en <= e.creado_en
                """, Long.class);
        assertThat(antesDeSuEntrada).isZero();

        // Kardex de cada producto cuyos lotes tienen todos su entrada: nunca negativo y cuadra con el stock.
        List<Long> productos = jdbcTemplate.queryForList("""
                SELECT p.id FROM productos p
                WHERE p.botica_id = ? AND EXISTS (SELECT 1 FROM lotes l WHERE l.producto_id = p.id)
                  AND NOT EXISTS (SELECT 1 FROM lotes l WHERE l.producto_id = p.id
                                  AND NOT EXISTS (SELECT 1 FROM movimientos_stock m
                                                  WHERE m.lote_id = l.id AND m.tipo = 'INGRESO'))
                """, Long.class, boticaId);
        assertThat(productos).isNotEmpty();
        for (Long productoId : productos) {
            assertInvariante(productoId, kardexService.consultar(productoId, null, null, 0, 1));
        }
    }

    /** Saldo final = stock de los lotes, y ningún punto del Kardex completo (sin paginar) queda negativo. */
    private void assertInvariante(Long productoId, KardexResponse k) {
        Long stockLotes = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(stock), 0) FROM lotes WHERE producto_id = ?", Long.class, productoId);
        assertThat(k.resumen().saldoFinal()).as("saldo final del Kardex del producto %d", productoId).isEqualTo(stockLotes);
        assertThat(k.resumen().stockActual()).isEqualTo(stockLotes);
        List<FilaKardexResponse> todas = kardexDao.listarFilas(boticaId, productoId, null, null, null, 0);
        assertThat(todas).as("saldo nunca negativo, producto %d", productoId).allMatch(f -> f.saldo() >= 0);
        assertThat(todas.getLast().saldo()).isEqualTo(stockLotes);
    }

    /** productoId y presentacionId ("Unidad", factor 1, S/ 2.00). */
    private long[] producto(String nombre, boolean requiereReceta) {
        String codigo = "QA-K-" + sufijo();
        jdbcTemplate.update("INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre, requiere_receta) " +
                "VALUES (?, ?, 'Otros', ?, 'unidad', ?)", boticaId, nombre, codigo, requiereReceta);
        Long productoId = jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? AND codigo_barras = ?", Long.class, boticaId, codigo);
        jdbcTemplate.update("INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) " +
                "VALUES (?, ?, 'Unidad', 1, 2.00)", boticaId, productoId);
        Long presentacionId = jdbcTemplate.queryForObject(
                "SELECT id FROM presentaciones WHERE producto_id = ?", Long.class, productoId);
        return new long[]{productoId, presentacionId};
    }

    /** PUT del catálogo para un producto creado con producto(): mismos datos, cambia solo requiereReceta. */
    private ProductoRequest requestCatalogo(long productoId, boolean requiereReceta) {
        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT nombre, codigo_barras FROM productos WHERE id = ?", productoId);
        Long presentacionId = jdbcTemplate.queryForObject(
                "SELECT id FROM presentaciones WHERE producto_id = ?", Long.class, productoId);
        return new ProductoRequest((String) fila.get("nombre"), null, "Otros", (String) fila.get("codigo_barras"), "unidad",
                requiereReceta, List.of(new PresentacionRequest(presentacionId, "Unidad", 1, new BigDecimal("2.00"))));
    }

    /** Lote insertado por SQL (sin su INGRESO): el test escribe los movimientos con fecha explícita. */
    private long lote(long productoId, int stock) {
        String codigo = "L-" + sufijo();
        jdbcTemplate.update("INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                "VALUES (?, ?, ?, CURRENT_DATE + 365, ?, 1.00)", boticaId, productoId, codigo, stock);
        return jdbcTemplate.queryForObject("SELECT id FROM lotes WHERE producto_id = ? AND codigo = ?",
                Long.class, productoId, codigo);
    }

    private void movimiento(long loteId, String tipo, int cantidad, LocalDate dia) {
        OffsetDateTime fecha = dia.atTime(12, 0).atZone(FechaNegocio.ZONA_LIMA).toOffsetDateTime();
        jdbcTemplate.update("INSERT INTO movimientos_stock (botica_id, lote_id, tipo, cantidad, creado_en, creado_por) " +
                "VALUES (?, ?, ?, ?, ?, ?)", boticaId, loteId, tipo, cantidad, fecha, contexto.usuarioId());
    }

    private static String sufijo() {
        return Long.toString(System.nanoTime() % 1_000_000_000L, 36);
    }
}
