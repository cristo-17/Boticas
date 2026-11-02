package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.exception.StockInsuficienteException;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T15 V1: la notificación de stock crítico nace de un evento publicado
 * dentro de la transacción de la venta. Tres preguntas, contra Postgres
 * real (botica_test):
 * 1. Venta con rollback -> ninguna notificación.
 * 2. Venta confirmada que deja stock crítico -> la notificación SÍ queda
 *    guardada (el listener corre después del commit: si escribe sin
 *    transacción propia, Spring no garantiza que se confirme).
 * 3. Listener que falla -> la venta queda igual (ver
 *    NotificacionListenerFallaIntegracionTest).
 * Sin @Transactional en la clase: mismo motivo que VentaRollbackIntegracionTest.
 */
@SpringBootTest
class NotificacionEventosIntegracionTest {

    @Autowired
    private VentaService ventaService;
    @Autowired
    private CajaDao cajaDao;
    @Autowired
    private ContextoOperacion contexto;
    @Autowired
    private FechaNegocio fechaNegocio;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long boticaId;

    @BeforeEach
    void asegurarCajaAbierta() {
        boticaId = contexto.boticaId();
        Long usuarioId = contexto.usuarioId();
        if (cajaDao.buscarAbiertaDelUsuario(boticaId, usuarioId).isEmpty()) {
            cajaDao.insertar(CajaDiaria.builder()
                    .boticaId(boticaId).usuarioId(usuarioId).fecha(fechaNegocio.hoy()).turno(contexto.turno())
                    .montoApertura(new BigDecimal("100.00")).horaApertura(fechaNegocio.ahora())
                    .build());
        }
    }

    @Test
    void ventaQueHaceRollback_noGeneraNotificacionDeStockCritico() {
        // Línea 1 quedaría en stock crítico (10 - 5 = 5 <= umbral 15); línea 2 no tiene stock y tumba la venta.
        ProductoQa critico = productoConStock(10);
        ProductoQa sinStock = productoConStock(1);

        NuevaVentaRequest pedido = new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(critico.productoId, critico.presentacionId, 5, "BUSQUEDA"),
                new ItemVentaRequest(sinStock.productoId, sinStock.presentacionId, 3, "BUSQUEDA")
        ), "efectivo");

        assertThatThrownBy(() -> ventaService.registrar(pedido)).isInstanceOf(StockInsuficienteException.class);

        assertThat(notificacionesDe(critico)).as("una venta revertida no puede avisar de un stock que nunca bajó").isZero();
    }

    @Test
    void ventaConfirmadaQueDejaStockCritico_guardaLaNotificacion() {
        ProductoQa critico = productoConStock(20);

        ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(critico.productoId, critico.presentacionId, 10, "BUSQUEDA")
        ), "efectivo"));

        assertThat(notificacionesDe(critico)).as("20 - 10 = 10 <= umbral 15: debe quedar UNA notificación guardada").isEqualTo(1);
    }

    @Test
    void ventaConfirmada_queNoLlegaAlUmbral_noNotifica() {
        ProductoQa holgado = productoConStock(100);

        VentaResponse venta = ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(holgado.productoId, holgado.presentacionId, 1, "BUSQUEDA")
        ), "efectivo"));

        assertThat(venta.id()).isNotNull();
        assertThat(notificacionesDe(holgado)).isZero();
    }

    // DIF-11 (Tarea 0B): el aviso cuenta el stock vendible del producto. Las 100 vencidas no
    // cuentan, así que quedan 10 vendibles y debe avisar. Pasa desde la corrección FEFO de la
    // Tarea 0 (la venta ya suma solo lotes no vencidos): queda como guardia, no nació en rojo.
    @Test
    void ventaQueDejaPocoStockVendible_notifica_aunqueHayaMuchoStockVencido() {
        ProductoQa critico = productoConStock(20);
        jdbcTemplate.update(
                "INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) VALUES (?, ?, ?, ?, 100, 1.00)",
                boticaId, critico.productoId, "QA-EV-V-" + System.nanoTime(), fechaNegocio.hoy().minusDays(1));

        ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                new ItemVentaRequest(critico.productoId, critico.presentacionId, 10, "BUSQUEDA")
        ), "efectivo"));

        assertThat(notificacionesDe(critico)).as("20 - 10 = 10 vendibles <= 15; las 100 vencidas no cuentan").isEqualTo(1);
    }

    private long notificacionesDe(ProductoQa p) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notificaciones WHERE botica_id = ? AND tipo = 'STOCK_CRITICO' AND titulo = ?",
                Long.class, boticaId, "Stock crítico: " + p.nombre);
    }

    private record ProductoQa(long productoId, long presentacionId, String nombre) {
    }

    private ProductoQa productoConStock(int stock) {
        String sufijo = String.valueOf(System.nanoTime() % 1_000_000_000_000L); // codigo_barras es VARCHAR(20)
        String nombre = "Producto evento QA " + sufijo;
        jdbcTemplate.update(
                "INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre) VALUES (?, ?, ?, ?, ?)",
                boticaId, nombre, "Otros", "QA-EV-" + sufijo, "unidad");
        Long productoId = jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? AND codigo_barras = ?", Long.class, boticaId, "QA-EV-" + sufijo);
        jdbcTemplate.update(
                "INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) VALUES (?, ?, 'Unidad', 1, 5.00)",
                boticaId, productoId);
        Long presentacionId = jdbcTemplate.queryForObject(
                "SELECT id FROM presentaciones WHERE producto_id = ?", Long.class, productoId);
        jdbcTemplate.update(
                "INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                        "VALUES (?, ?, ?, CURRENT_DATE + INTERVAL '1 year', ?, 1.00)",
                boticaId, productoId, "QA-EV-L-" + sufijo, stock);
        return new ProductoQa(productoId, presentacionId, nombre);
    }
}
