package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.ResolverRevisionRequest;
import com.botica.backend.dto.RevisionVentaResponse;
import com.botica.backend.dto.SinConexionRequest;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.exception.NegocioException;
import com.botica.backend.exception.SinPermisoException;
import com.botica.backend.exception.StockInsuficienteException;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 22 (D-E2-7, docs/DISENO-OFFLINE.md): una venta con sinConexion ya
 * ocurrió en el mostrador. Se registra siempre y cada problema queda en
 * revisiones_venta; nunca se pierde ni se duplica.
 */
@SpringBootTest
@Transactional
class VentaSinConexionIntegracionTest {

    @Autowired
    private VentaService ventaService;
    @Autowired
    private RevisionVentaService revisionService;
    @Autowired
    private CajaService cajaService;
    @Autowired
    private CajaDao cajaDao;
    @Autowired
    private ContextoOperacion contexto;
    @Autowired
    private FechaNegocio fechaNegocio;
    @Autowired
    private JdbcTemplate jdbc;

    private Long cajaId;

    @BeforeEach
    void preparar() {
        Long boticaId = contexto.boticaId();
        cajaId = cajaDao.buscarAbiertaDelUsuario(boticaId, contexto.usuarioId())
                .orElseGet(() -> cajaDao.insertar(CajaDiaria.builder()
                        .boticaId(boticaId).usuarioId(contexto.usuarioId()).fecha(fechaNegocio.hoy()).turno(contexto.turno())
                        .montoApertura(new BigDecimal("100.00")).horaApertura(fechaNegocio.ahora())
                        .build()))
                .getId();
    }

    @Test
    void reenviarLaMismaClave_devuelveLaMismaVenta_yHayUnaSola() {
        long[] p = producto("Idem QA", false, "3.00", 50);
        UUID clave = UUID.randomUUID();
        NuevaVentaRequest pedido = sinConexion(clave, cajaId, List.of(item(p, 2, "3.00")));

        VentaResponse primera = ventaService.registrar(pedido);
        VentaResponse segunda = ventaService.registrar(pedido);

        assertThat(segunda.id()).isEqualTo(primera.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ventas WHERE clave_idempotencia = ?", Long.class, clave)).isEqualTo(1L);
        assertThat(stock(p)).as("el stock se descuenta una sola vez").isEqualTo(48);
        assertThat(primera.vendidaSinConexion()).isTrue();
        assertThat(primera.horaDispositivo()).isNotNull();
        assertThat(primera.revisiones()).isEmpty();
        Map<String, Object> fila = jdbc.queryForMap("SELECT vendida_sin_conexion, hora_dispositivo FROM ventas WHERE id = ?", primera.id());
        assertThat(fila.get("vendida_sin_conexion")).isEqualTo(true);
        assertThat(fila.get("hora_dispositivo")).isNotNull();
    }

    @Test
    void stockInsuficiente_registraLaVenta_conLineaSinLoteYRevision() {
        long[] p = producto("Faltante QA", false, "2.00", 3);

        VentaResponse venta = ventaService.registrar(sinConexion(UUID.randomUUID(), cajaId, List.of(item(p, 5, "2.00"))));

        assertThat(venta.total()).as("se cobró todo").isEqualByComparingTo("10.00");
        assertThat(stock(p)).as("nunca negativo").isZero();
        List<Map<String, Object>> lineas = jdbc.queryForList(
                "SELECT lote_id, costo_unitario, cantidad, total_linea FROM venta_detalle WHERE venta_id = ? ORDER BY id", venta.id());
        assertThat(lineas).hasSize(2);
        assertThat(lineas.get(0).get("lote_id")).isNotNull();
        assertThat(lineas.get(0).get("cantidad")).isEqualTo(3);
        assertThat(lineas.get(1).get("lote_id")).isNull();
        assertThat(lineas.get(1).get("costo_unitario")).isNull();
        assertThat(lineas.get(1).get("cantidad")).isEqualTo(2);
        BigDecimal suma = jdbc.queryForObject("SELECT SUM(total_linea) FROM venta_detalle WHERE venta_id = ?", BigDecimal.class, venta.id());
        assertThat(suma).as("las líneas suman el total cobrado").isEqualByComparingTo(venta.total());
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(-cantidad), 0) FROM movimientos_stock WHERE referencia_venta_id = ?",
                Integer.class, venta.id())).as("solo se mueve lo que había").isEqualTo(3);

        assertThat(venta.revisiones()).singleElement().satisfies(r -> {
            assertThat(r.tipo()).isEqualTo("STOCK_INSUFICIENTE");
            assertThat(r.unidadesFaltantes()).isEqualTo(2);
            assertThat(r.estado()).isEqualTo("PENDIENTE");
        });
        assertThat(revisionService.contarVentasPorRevisar()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void enLinea_elStockInsuficienteSigueRechazando() {
        long[] p = producto("En linea QA", false, "2.00", 1);
        NuevaVentaRequest pedido = new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(p[0], p[1], 3, "BUSQUEDA")), "efectivo", null);

        assertThatThrownBy(() -> ventaService.registrar(pedido)).isInstanceOf(StockInsuficienteException.class);
    }

    @Test
    void precioDistinto_seRespetaElCobrado_yQuedaAnotado() {
        long[] p = producto("Precio QA", false, "4.00", 20);

        VentaResponse venta = ventaService.registrar(sinConexion(UUID.randomUUID(), cajaId, List.of(item(p, 2, "3.50"))));

        assertThat(venta.total()).isEqualByComparingTo("7.00");
        assertThat(venta.revisiones()).singleElement().satisfies(r -> {
            assertThat(r.tipo()).isEqualTo("PRECIO_DISTINTO");
            assertThat(r.precioCobrado()).isEqualByComparingTo("3.50");
            assertThat(r.precioVigente()).isEqualByComparingTo("4.00");
        });
    }

    @Test
    void enLinea_precioCobradoSeIgnora() {
        long[] p = producto("Precio en linea QA", false, "4.00", 20);
        VentaResponse venta = ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(),
                List.of(new ItemVentaRequest(p[0], p[1], 2, "BUSQUEDA", new BigDecimal("1.00"))), "efectivo", null));

        assertThat(venta.total()).isEqualByComparingTo("8.00");
        assertThat(venta.vendidaSinConexion()).isFalse();
    }

    @Test
    void cajaCerrada_laVentaVaASuCaja_sinRecalcularElCierre() {
        long[] p = producto("Caja cerrada QA", false, "5.00", 20);
        Long cerrada = jdbc.queryForObject("INSERT INTO caja_diaria (botica_id, usuario_id, fecha, turno, monto_apertura, hora_cierre, " +
                "monto_contado, monto_esperado, diferencia, semaforo_descuadre, estado) VALUES (?, ?, CURRENT_DATE - 1, 'Mañana', 50, now(), " +
                "60, 50, 10, 'LEVE', 'CERRADA') RETURNING id", Long.class, contexto.boticaId(), contexto.usuarioId());

        VentaResponse venta = ventaService.registrar(sinConexion(UUID.randomUUID(), cerrada, List.of(item(p, 2, "5.00"))));

        assertThat(jdbc.queryForObject("SELECT caja_id FROM ventas WHERE id = ?", Long.class, venta.id())).isEqualTo(cerrada);
        Map<String, Object> caja = jdbc.queryForMap("SELECT estado, monto_esperado, diferencia FROM caja_diaria WHERE id = ?", cerrada);
        assertThat(caja.get("estado")).isEqualTo("CERRADA");
        assertThat((BigDecimal) caja.get("monto_esperado")).isEqualByComparingTo("50");
        assertThat((BigDecimal) caja.get("diferencia")).isEqualByComparingTo("10");
        assertThat(venta.revisiones()).singleElement().satisfies(r -> {
            assertThat(r.tipo()).isEqualTo("CAJA_CERRADA");
            assertThat(r.detalle()).contains("10");
        });
    }

    @Test
    void cajaDeOtroUsuario_403_nuncaSeAtribuyeAOtro() {
        Long otro = jdbc.queryForObject("SELECT id FROM usuarios WHERE botica_id = ? AND id <> ? ORDER BY id LIMIT 1",
                Long.class, contexto.boticaId(), contexto.usuarioId());
        Long ajena = jdbc.queryForObject("INSERT INTO caja_diaria (botica_id, usuario_id, fecha, turno, monto_apertura, estado) " +
                "VALUES (?, ?, CURRENT_DATE, 'Noche', 10, 'CERRADA') RETURNING id", Long.class, contexto.boticaId(), otro);
        long[] p = producto("Ajena QA", false, "1.00", 5);

        assertThatThrownBy(() -> ventaService.registrar(sinConexion(UUID.randomUUID(), ajena, List.of(item(p, 1, "1.00")))))
                .isInstanceOf(SinPermisoException.class);
    }

    @Test
    void sinPrecioCobrado_400FormatoInvalido() {
        long[] p = producto("Sin precio QA", false, "1.00", 5);
        NuevaVentaRequest pedido = new NuevaVentaRequest(UUID.randomUUID(), List.of(new ItemVentaRequest(p[0], p[1], 1, "BUSQUEDA")),
                "efectivo", null, new SinConexionRequest(cajaId, OffsetDateTime.now()));

        assertThatThrownBy(() -> ventaService.registrar(pedido))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo("FORMATO_INVALIDO"));
    }

    @Test
    void productoDeRecetaSinReceta_seRegistraConRevision() {
        long[] p = producto("Receta QA", true, "6.00", 10);

        VentaResponse venta = ventaService.registrar(sinConexion(UUID.randomUUID(), cajaId, List.of(item(p, 1, "6.00"))));

        assertThat(venta.receta()).isNull();
        assertThat(venta.revisiones()).singleElement().satisfies(r -> {
            assertThat(r.tipo()).isEqualTo("OTRO");
            assertThat(r.detalle()).contains("Receta QA");
        });
    }

    @Test
    void productoDesactivado_seRegistraConRevision() {
        long[] p = producto("Inactivo QA", false, "2.00", 10);
        jdbc.update("UPDATE productos SET activo = false WHERE id = ?", p[0]);

        VentaResponse venta = ventaService.registrar(sinConexion(UUID.randomUUID(), cajaId, List.of(item(p, 1, "2.00"))));

        assertThat(venta.revisiones()).extracting(RevisionVentaResponse::tipo).containsExactly("OTRO");
    }

    @Test
    void resolver_faltanteExigeNota_yNoSeReabre() {
        long[] p = producto("Resolver QA", false, "2.00", 0);
        VentaResponse venta = ventaService.registrar(sinConexion(UUID.randomUUID(), cajaId, List.of(item(p, 1, "2.00"))));
        Long id = venta.revisiones().get(0).id();

        assertThatThrownBy(() -> revisionService.resolver(id, new ResolverRevisionRequest("FALTANTE", " ")))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo("RESOLUCION_INVALIDA"));

        RevisionVentaResponse resuelta = revisionService.resolver(id, new ResolverRevisionRequest("FALTANTE", "Se rompió y no se registró"));
        assertThat(resuelta.estado()).isEqualTo("RESUELTA");
        assertThat(resuelta.resolucion()).isEqualTo("FALTANTE");
        assertThat(resuelta.resueltaPorNombre()).isNotBlank();

        assertThatThrownBy(() -> revisionService.resolver(id, new ResolverRevisionRequest("VISTA", null)))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo("REVISION_YA_RESUELTA"));
    }

    @Test
    void faltanteSoloParaStockInsuficiente() {
        long[] p = producto("Precio faltante QA", false, "4.00", 10);
        VentaResponse venta = ventaService.registrar(sinConexion(UUID.randomUUID(), cajaId, List.of(item(p, 1, "3.00"))));

        assertThatThrownBy(() -> revisionService.resolver(venta.revisiones().get(0).id(), new ResolverRevisionRequest("FALTANTE", "x")))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo("RESOLUCION_INVALIDA"));
    }

    @Test
    void resumenCierre_cuentaLasVentasSinConexion_sinMonto() {
        long[] p = producto("Resumen QA", false, "1.00", 10);
        long antes = cajaService.obtenerResumenCierre(cajaId).cantidadVentasSinConexion();
        ventaService.registrar(sinConexion(UUID.randomUUID(), cajaId, List.of(item(p, 1, "1.00"))));

        assertThat(cajaService.obtenerResumenCierre(cajaId).cantidadVentasSinConexion()).isEqualTo(antes + 1);
    }

    // Uno por test: tras una violación, Postgres aborta la transacción del test.
    @Test
    void elCheckImpideLoteSinCosto() {
        VentaResponse venta = ventaEnLinea("Check costo QA");
        assertThatThrownBy(() -> jdbc.update("UPDATE venta_detalle SET costo_unitario = NULL WHERE venta_id = ?", venta.id()))
                .hasMessageContaining("venta_detalle_lote_y_costo_juntos");
    }

    @Test
    void elCheckImpideHoraDeDispositivoEnUnaVentaEnLinea() {
        VentaResponse venta = ventaEnLinea("Check hora QA");
        assertThatThrownBy(() -> jdbc.update("UPDATE ventas SET hora_dispositivo = now() WHERE id = ?", venta.id()))
                .hasMessageContaining("ventas_hora_dispositivo_solo_sin_conexion");
    }

    private VentaResponse ventaEnLinea(String nombre) {
        long[] p = producto(nombre, false, "1.00", 10);
        return ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(),
                List.of(new ItemVentaRequest(p[0], p[1], 1, "BUSQUEDA")), "efectivo", null));
    }

    private static NuevaVentaRequest sinConexion(UUID clave, Long cajaId, List<ItemVentaRequest> items) {
        return new NuevaVentaRequest(clave, items, "efectivo", null,
                new SinConexionRequest(cajaId, OffsetDateTime.parse("2026-11-03T14:05:12-05:00")));
    }

    private static ItemVentaRequest item(long[] producto, int cantidad, String precio) {
        return new ItemVentaRequest(producto[0], producto[1], cantidad, "BUSQUEDA", new BigDecimal(precio));
    }

    private int stock(long[] producto) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(stock), 0) FROM lotes WHERE producto_id = ?", Integer.class, producto[0]);
    }

    private long[] producto(String nombre, boolean requiereReceta, String precio, int stock) {
        Long boticaId = contexto.boticaId();
        String codigo = "QA-SC-" + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        Long productoId = jdbc.queryForObject("INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre, requiere_receta) " +
                "VALUES (?, ?, 'Otros', ?, 'unidad', ?) RETURNING id", Long.class, boticaId, nombre, codigo, requiereReceta);
        Long presentacionId = jdbc.queryForObject("INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) " +
                "VALUES (?, ?, 'Unidad', 1, ?) RETURNING id", Long.class, boticaId, productoId, new BigDecimal(precio));
        if (stock > 0) {
            jdbc.update("INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                    "VALUES (?, ?, ?, CURRENT_DATE + 365, ?, 1.00)", boticaId, productoId, "L-" + codigo, stock);
        }
        return new long[]{productoId, presentacionId};
    }
}
