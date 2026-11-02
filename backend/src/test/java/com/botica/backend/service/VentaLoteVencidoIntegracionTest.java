package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.exception.StockInsuficienteException;
import com.botica.backend.exception.StockSoloVencidoException;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 0 (entrega final), punto 4: un lote vencido nunca se dispensa.
 * FEFO ordena por fecha_vencimiento, así que un lote vencido con stock
 * sería justo el PRIMERO en consumirse si el SQL no lo excluye. El
 * filtro vive en el SQL de LoteDaoJdbc.bloquearLotesFefo, por eso es un
 * test con BD real: un mock del DAO no lo vería.
 *
 * "Vencido" = fecha_vencimiento anterior a hoy en Lima (FechaNegocio),
 * el mismo criterio que el estado VENCIDO de LoteDaoJdbc. Un lote que
 * vence HOY todavía se vende.
 *
 * Sin @Transactional en la clase, por el mismo motivo que
 * VentaRollbackIntegracionTest.
 */
@SpringBootTest
class VentaLoteVencidoIntegracionTest {

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
    void venta_conLoteVencidoYLoteVigente_consumeSoloElVigente() {
        LocalDate hoy = fechaNegocio.hoy();
        long[] producto = productoConPresentacionUnidad();
        long loteVencido = lote(producto[0], hoy.minusDays(1), 50);
        long loteVigente = lote(producto[0], hoy.plusYears(1), 50);

        ventaService.registrar(pedido(producto, 5));

        assertThat(stockDelLote(loteVencido))
                .as("el lote vencido ayer vence antes, FEFO lo tomaría primero: no debe tocarse")
                .isEqualTo(50);
        assertThat(stockDelLote(loteVigente)).isEqualTo(45);
    }

    @Test
    void venta_conSoloStockVencido_esStockSoloVencido_yNoDescuentaNada() {
        long[] producto = productoConPresentacionUnidad();
        long loteVencido = lote(producto[0], fechaNegocio.hoy().minusDays(1), 50);

        // DIF-13 (Tarea 0B): el error dice que es vencido, no "disponible 0".
        assertThatThrownBy(() -> ventaService.registrar(pedido(producto, 5)))
                .isInstanceOf(StockSoloVencidoException.class)
                .hasMessageContaining("Retíralo con una merma");
        assertThat(stockDelLote(loteVencido)).isEqualTo(50);
    }

    @Test
    void venta_conPocoStockVigenteYMuchoVencido_sigueSiendoStockInsuficiente() {
        LocalDate hoy = fechaNegocio.hoy();
        long[] producto = productoConPresentacionUnidad();
        lote(producto[0], hoy.minusDays(1), 50);
        long loteVigente = lote(producto[0], hoy.plusYears(1), 3);

        assertThatThrownBy(() -> ventaService.registrar(pedido(producto, 5)))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("disponible 3");
        assertThat(stockDelLote(loteVigente)).isEqualTo(3);
    }

    @Test
    void venta_conLoteQueVenceHoy_todaviaSeVende() {
        long[] producto = productoConPresentacionUnidad();
        long loteVenceHoy = lote(producto[0], fechaNegocio.hoy(), 50);

        ventaService.registrar(pedido(producto, 5));

        assertThat(stockDelLote(loteVenceHoy)).isEqualTo(45);
    }

    private NuevaVentaRequest pedido(long[] producto, int cantidad) {
        return new NuevaVentaRequest(UUID.randomUUID(),
                List.of(new ItemVentaRequest(producto[0], producto[1], cantidad, "BUSQUEDA")), "efectivo");
    }

    /** productoId, presentacionId ("Unidad", factor 1) -- nuevos, aislados del resto del seed. */
    private long[] productoConPresentacionUnidad() {
        String codigoBarras = "QV" + System.nanoTime(); // codigo_barras es VARCHAR(20)
        jdbcTemplate.update(
                "INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre) VALUES (?, ?, ?, ?, ?)",
                boticaId, "Producto vencimiento QA", "Otros", codigoBarras, "unidad");
        Long productoId = jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? AND codigo_barras = ?", Long.class, boticaId, codigoBarras);

        jdbcTemplate.update(
                "INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) VALUES (?, ?, 'Unidad', 1, 5.00)",
                boticaId, productoId);
        Long presentacionId = jdbcTemplate.queryForObject(
                "SELECT id FROM presentaciones WHERE producto_id = ?", Long.class, productoId);

        return new long[]{productoId, presentacionId};
    }

    private long lote(long productoId, LocalDate fechaVencimiento, int stock) {
        String codigoLote = "QA-VENC-L-" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) VALUES (?, ?, ?, ?, ?, 1.00)",
                boticaId, productoId, codigoLote, fechaVencimiento, stock);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM lotes WHERE producto_id = ? AND codigo = ?", Long.class, productoId, codigoLote);
    }

    private int stockDelLote(long loteId) {
        return jdbcTemplate.queryForObject("SELECT stock FROM lotes WHERE id = ?", Integer.class, loteId);
    }
}
