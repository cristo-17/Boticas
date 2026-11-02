package com.botica.backend.dao;

import com.botica.backend.dto.FilaResultadoResponse;
import com.botica.backend.dto.ReporteResultadosResponse;
import com.botica.backend.dto.TotalesResultadoResponse;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.model.MovimientoCaja;
import com.botica.backend.service.ReporteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración contra PostgreSQL ({@code botica_test}) para el reporte
 * de resultados (Tarea 18, D-E2-3):
 * <ol>
 *   <li>Agrupación por día calendario de Lima: una venta registrada a las 23:30
 *       hora de Lima ({@code 04:30Z} del día siguiente en UTC) cae en su fecha de Lima,
 *       y con {@code agrupar=dia} aparecen todos los días del rango (incluidos en cero
 *       los que no tuvieron movimientos).</li>
 *   <li>Consistencia {@code agrupar=dia} vs {@code agrupar=mes}: la suma de los días de
 *       un mes coincide exactamente con el total mensual y con {@code totales}.</li>
 *   <li>Consistencia con las cajas del día: las ventas de un día en el reporte coinciden
 *       con la suma de ventas de las cajas de ese día.</li>
 *   <li>Merma valorizada al costo de compra del lote ({@code lotes.costo_unitario}),
 *       nunca al precio de venta ({@code presentaciones.precio}).</li>
 * </ol>
 */
@SpringBootTest
@Transactional
class ReporteResultadosIntegracionTest {

    @Autowired
    private ReporteService reporteService;
    @Autowired
    private CajaDao cajaDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long boticaId;
    private Long usuarioId;
    private Long usuario2Id;

    @BeforeEach
    void crearBoticaYUsuariosAislados() {
        String ruc = String.valueOf(10000000000L + (System.nanoTime() % 89999999999L));
        jdbcTemplate.update(
                "INSERT INTO boticas (nombre, ruc, direccion) VALUES ('Botica Test Reportes', ?, 'Av. Lima 100')",
                ruc);
        boticaId = jdbcTemplate.queryForObject(
                "SELECT id FROM boticas WHERE ruc = ?", Long.class, ruc);

        String u1 = "rep.u1." + System.nanoTime();
        String u2 = "rep.u2." + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO usuarios (botica_id, rol_id, nombre, usuario, password_hash) " +
                        "VALUES (?, (SELECT id FROM roles WHERE nombre = 'ADMINISTRADOR'), 'Admin Rep', ?, 'x')",
                boticaId, u1);
        jdbcTemplate.update(
                "INSERT INTO usuarios (botica_id, rol_id, nombre, usuario, password_hash) " +
                        "VALUES (?, (SELECT id FROM roles WHERE nombre = 'TECNICO'), 'Tec Rep', ?, 'x')",
                boticaId, u2);
        usuarioId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE usuario = ?", Long.class, u1);
        usuario2Id = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE usuario = ?", Long.class, u2);
    }

    @Test
    void ventaALas2330HoraDeLima_caeEnSuDiaDeLima_yDiasSinMovimientoAparecenEnCero() {
        Long productoId = insertarProducto("REP-LIMA-1", new BigDecimal("15.00"));
        Long presentacionId = obtenerPresentacionUnidadId(productoId);
        Long loteId = insertarLote(productoId, "L-LIMA-1", new BigDecimal("9.00"));
        Long cajaId = abrirCaja(usuarioId, LocalDate.of(2026, 3, 10), "Noche");

        // 2026-03-10 a las 23:30 en Lima (-05:00) es 2026-03-11T04:30:00Z en UTC.
        OffsetDateTime venta2330Lima = OffsetDateTime.parse("2026-03-10T23:30:00-05:00");
        insertarVentaConDetalle(
                cajaId, venta2330Lima, "efectivo",
                productoId, presentacionId, loteId, 2,
                new BigDecimal("15.00"), new BigDecimal("9.00"), new BigDecimal("30.00"));

        ReporteResultadosResponse rep = reporteService.obtenerResultados(
                boticaId,
                LocalDate.of(2026, 3, 9),
                LocalDate.of(2026, 3, 11),
                "dia");

        // Aparecen los 3 días del rango, incluidos 2026-03-09 y 2026-03-11 en cero.
        assertThat(rep.filas()).hasSize(3);

        FilaResultadoResponse dia09 = rep.filas().get(0);
        assertThat(dia09.periodo()).isEqualTo("2026-03-09");
        assertThat(dia09.ventas()).isEqualByComparingTo("0.00");
        assertThat(dia09.resultado()).isEqualByComparingTo("0.00");

        FilaResultadoResponse dia10 = rep.filas().get(1);
        assertThat(dia10.periodo()).isEqualTo("2026-03-10");
        assertThat(dia10.ventas()).isEqualByComparingTo("30.00");
        assertThat(dia10.costoVentas()).isEqualByComparingTo("18.00");
        assertThat(dia10.margenBruto()).isEqualByComparingTo("12.00");
        assertThat(dia10.resultado()).isEqualByComparingTo("12.00");

        // En UTC ya era 2026-03-11 (04:30Z), pero en Lima sigue siendo 2026-03-10: el día 11 queda en 0.00.
        FilaResultadoResponse dia11 = rep.filas().get(2);
        assertThat(dia11.periodo()).isEqualTo("2026-03-11");
        assertThat(dia11.ventas()).isEqualByComparingTo("0.00");
        assertThat(dia11.resultado()).isEqualByComparingTo("0.00");
    }

    @Test
    void sumaDeLosDiasDeUnMes_coincideExactamenteConElTotalDelMes() {
        Long productoId = insertarProducto("REP-MES-1", new BigDecimal("25.00"));
        Long presentacionId = obtenerPresentacionUnidadId(productoId);
        Long loteId = insertarLote(productoId, "L-MES-1", new BigDecimal("10.00"));
        Long cajaId = abrirCaja(usuarioId, LocalDate.of(2026, 4, 5), "Mañana");

        // Día 5 de abril: venta de 2 u (S/ 50.00, costo S/ 20.00) + egreso de S/ 8.50
        insertarVentaConDetalle(
                cajaId, OffsetDateTime.parse("2026-04-05T10:15:00-05:00"), "efectivo",
                productoId, presentacionId, loteId, 2,
                new BigDecimal("25.00"), new BigDecimal("10.00"), new BigDecimal("50.00"));
        cajaDao.insertarMovimiento(MovimientoCaja.builder()
                .boticaId(boticaId).cajaId(cajaId).tipo("egreso")
                .descripcion("Servicios básicos").nota("Agua").monto(new BigDecimal("-8.50"))
                .afectaEfectivo(true).creadoPor(usuarioId)
                .creadoEn(OffsetDateTime.parse("2026-04-05T12:00:00-05:00"))
                .build());

        // Día 18 de abril: venta a las 23:45 Lima (S/ 75.00, costo S/ 30.00) + merma de 1 u (costo S/ 10.00)
        insertarVentaConDetalle(
                cajaId, OffsetDateTime.parse("2026-04-18T23:45:00-05:00"), "yape",
                productoId, presentacionId, loteId, 3,
                new BigDecimal("25.00"), new BigDecimal("10.00"), new BigDecimal("75.00"));
        insertarMerma(loteId, 1, new BigDecimal("25.00"), OffsetDateTime.parse("2026-04-18T18:00:00-05:00"));

        LocalDate inicioMes = LocalDate.of(2026, 4, 1);
        LocalDate finMes = LocalDate.of(2026, 4, 30);

        ReporteResultadosResponse porDia = reporteService.obtenerResultados(boticaId, inicioMes, finMes, "dia");
        ReporteResultadosResponse porMes = reporteService.obtenerResultados(boticaId, inicioMes, finMes, "mes");

        assertThat(porDia.filas()).hasSize(30);
        assertThat(porMes.filas()).hasSize(1);

        BigDecimal sumaVentasDias = porDia.filas().stream()
                .map(FilaResultadoResponse::ventas).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumaCostoDias = porDia.filas().stream()
                .map(FilaResultadoResponse::costoVentas).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumaMargenDias = porDia.filas().stream()
                .map(FilaResultadoResponse::margenBruto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumaMermasDias = porDia.filas().stream()
                .map(FilaResultadoResponse::mermasCosto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumaEgresosDias = porDia.filas().stream()
                .map(FilaResultadoResponse::egresos).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumaResultadoDias = porDia.filas().stream()
                .map(FilaResultadoResponse::resultado).reduce(BigDecimal.ZERO, BigDecimal::add);

        FilaResultadoResponse filaMes = porMes.filas().get(0);
        assertThat(filaMes.periodo()).isEqualTo("2026-04");
        assertThat(sumaVentasDias).isEqualByComparingTo(filaMes.ventas()).isEqualByComparingTo("125.00");
        assertThat(sumaCostoDias).isEqualByComparingTo(filaMes.costoVentas()).isEqualByComparingTo("50.00");
        assertThat(sumaMargenDias).isEqualByComparingTo(filaMes.margenBruto()).isEqualByComparingTo("75.00");
        assertThat(sumaMermasDias).isEqualByComparingTo(filaMes.mermasCosto()).isEqualByComparingTo("10.00");
        assertThat(sumaEgresosDias).isEqualByComparingTo(filaMes.egresos()).isEqualByComparingTo("8.50");
        assertThat(sumaResultadoDias).isEqualByComparingTo(filaMes.resultado()).isEqualByComparingTo("56.50");

        TotalesResultadoResponse totales = porDia.totales();
        assertThat(totales.ventas()).isEqualByComparingTo(filaMes.ventas());
        assertThat(totales.resultado()).isEqualByComparingTo(filaMes.resultado());
    }

    @Test
    void ventasDeUnDiaEnElReporte_coincidenConLaSumaDeVentasDeLasCajasDeEseDia() {
        LocalDate dia = LocalDate.of(2026, 5, 15);
        Long productoId = insertarProducto("REP-CAJA-1", new BigDecimal("20.00"));
        Long presentacionId = obtenerPresentacionUnidadId(productoId);
        Long loteId = insertarLote(productoId, "L-CAJA-1", new BigDecimal("12.00"));

        Long cajaManana = abrirCaja(usuarioId, dia, "Mañana");
        Long cajaTarde = abrirCaja(usuario2Id, dia, "Tarde");

        insertarVentaConDetalle(
                cajaManana, OffsetDateTime.parse("2026-05-15T09:30:00-05:00"), "efectivo",
                productoId, presentacionId, loteId, 2,
                new BigDecimal("20.00"), new BigDecimal("12.00"), new BigDecimal("40.00"));
        insertarVentaConDetalle(
                cajaManana, OffsetDateTime.parse("2026-05-15T11:15:00-05:00"), "yape",
                productoId, presentacionId, loteId, 1,
                new BigDecimal("20.00"), new BigDecimal("12.00"), new BigDecimal("20.00"));
        insertarVentaConDetalle(
                cajaTarde, OffsetDateTime.parse("2026-05-15T17:45:00-05:00"), "tarjeta",
                productoId, presentacionId, loteId, 3,
                new BigDecimal("20.00"), new BigDecimal("12.00"), new BigDecimal("60.00"));

        BigDecimal totalCajas = cajaDao.sumarMovimientosEfectivo(boticaId, cajaManana)
                .add(cajaDao.calcularResumen(boticaId, cajaManana).totalVentasDigital())
                .add(cajaDao.sumarMovimientosEfectivo(boticaId, cajaTarde))
                .add(cajaDao.calcularResumen(boticaId, cajaTarde).totalVentasDigital());

        ReporteResultadosResponse rep = reporteService.obtenerResultados(boticaId, dia, dia, "dia");
        assertThat(rep.filas()).hasSize(1);
        assertThat(rep.filas().get(0).ventas())
                .isEqualByComparingTo(totalCajas)
                .isEqualByComparingTo("120.00");
    }

    @Test
    void mermaDeLoteConCosto5YPrecioVenta50_resta5EnElReporteYNo50() {
        LocalDate dia = LocalDate.of(2026, 5, 20);
        Long productoId = insertarProducto("REP-MERMA-1", new BigDecimal("50.00"));
        Long loteId = insertarLote(productoId, "L-MERMA-1", new BigDecimal("5.00"));

        insertarMerma(loteId, 1, new BigDecimal("50.00"), OffsetDateTime.parse("2026-05-20T14:00:00-05:00"));

        ReporteResultadosResponse rep = reporteService.obtenerResultados(boticaId, dia, dia, "dia");
        assertThat(rep.filas()).hasSize(1);
        FilaResultadoResponse fila = rep.filas().get(0);
        assertThat(fila.mermasCosto()).isEqualByComparingTo("5.00");
        assertThat(fila.resultado()).isEqualByComparingTo("-5.00");
    }

    private Long insertarProducto(String codigoBarras, BigDecimal precioVenta) {
        jdbcTemplate.update(
                "INSERT INTO productos (botica_id, nombre, laboratorio, categoria, codigo_barras, unidad_nombre) " +
                        "VALUES (?, 'Prod ' || ?, 'Lab Test', 'Analgésicos', ?, 'tableta')",
                boticaId, codigoBarras, codigoBarras);
        Long productoId = jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? AND codigo_barras = ?",
                Long.class, boticaId, codigoBarras);
        jdbcTemplate.update(
                "INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) " +
                        "VALUES (?, ?, 'Unidad', 1, ?)",
                boticaId, productoId, precioVenta);
        return productoId;
    }

    private Long obtenerPresentacionUnidadId(Long productoId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM presentaciones WHERE botica_id = ? AND producto_id = ? AND factor_conversion = 1",
                Long.class, boticaId, productoId);
    }

    private Long insertarLote(Long productoId, String codigoLote, BigDecimal costoUnitario) {
        jdbcTemplate.update(
                "INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                        "VALUES (?, ?, ?, DATE '2027-12-31', 100, ?)",
                boticaId, productoId, codigoLote, costoUnitario);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM lotes WHERE botica_id = ? AND producto_id = ? AND codigo = ?",
                Long.class, boticaId, productoId, codigoLote);
    }

    private Long abrirCaja(Long uid, LocalDate fecha, String turno) {
        return cajaDao.insertar(CajaDiaria.builder()
                .boticaId(boticaId).usuarioId(uid).fecha(fecha).turno(turno)
                .montoApertura(new BigDecimal("100.00"))
                .horaApertura(fecha.atTime(8, 0).atOffset(OffsetDateTime.parse("2026-01-01T00:00:00-05:00").getOffset()))
                .build()).getId();
    }

    private void insertarVentaConDetalle(
            Long cajaId, OffsetDateTime fecha, String metodoPago,
            Long productoId, Long presentacionId, Long loteId, int cantidad,
            BigDecimal precioUnitario, BigDecimal costoUnitario, BigDecimal totalLinea) {
        UUID clave = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO ventas (botica_id, usuario_id, caja_id, fecha, subtotal, igv, igv_tasa, total, metodo_pago, clave_idempotencia) " +
                        "VALUES (?, ?, ?, ?, ?, 0.00, 0.1800, ?, ?, ?)",
                boticaId, usuarioId, cajaId, fecha, totalLinea, totalLinea, metodoPago, clave);
        Long ventaId = jdbcTemplate.queryForObject(
                "SELECT id FROM ventas WHERE clave_idempotencia = ?", Long.class, clave);
        jdbcTemplate.update(
                "INSERT INTO venta_detalle (venta_id, botica_id, producto_id, presentacion_id, lote_id, " +
                        "nombre_producto, etiqueta_presentacion, cantidad, precio_unitario, costo_unitario, total_linea, origen_captura) " +
                        "VALUES (?, ?, ?, ?, ?, 'Prod Test', 'Unidad', ?, ?, ?, ?, 'BUSQUEDA')",
                ventaId, boticaId, productoId, presentacionId, loteId, cantidad, precioUnitario, costoUnitario, totalLinea);
        cajaDao.insertarMovimiento(MovimientoCaja.builder()
                .boticaId(boticaId).cajaId(cajaId).tipo("venta")
                .descripcion("Venta #" + ventaId).nota(null).monto(totalLinea)
                .afectaEfectivo("efectivo".equals(metodoPago))
                .creadoPor(usuarioId).creadoEn(fecha)
                .build());
    }

    private void insertarMerma(Long loteId, int cantidad, BigDecimal valorVenta, OffsetDateTime fecha) {
        jdbcTemplate.update(
                "INSERT INTO mermas (botica_id, lote_id, usuario_id, cantidad, motivo, observacion, valor_venta, fecha) " +
                        "VALUES (?, ?, ?, ?, 'Vencimiento', 'Prueba reporte', ?, ?)",
                boticaId, loteId, usuarioId, cantidad, valorVenta, fecha);
    }
}
