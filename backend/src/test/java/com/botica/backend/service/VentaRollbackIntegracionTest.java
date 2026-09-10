package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
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
 * Tarea 13, sección C: el caso que de verdad importa de una venta
 * multilínea — si la transacción estuviera mal delimitada, la primera
 * línea (con stock de sobra) dejaría inventario fantasma descontado
 * sin ninguna venta que lo explique, aunque la segunda línea (sin
 * stock) haga fallar TODO el pedido. Verificado manualmente por SQL en
 * la Tarea 11 Bloque B (docs/ESTADO.md) — automatizado acá.
 *
 * Sin @Transactional en la clase, a propósito (mismo motivo que
 * VentaConcurrenciaTest): la transacción real de VentaService tiene
 * que COMPLETAR su propio rollback de verdad antes de que esta consulta
 * lea el stock — si el test entero fuera una transacción, la lectura
 * vería el UPDATE todavía sin revertir (el rollback real ocurre recién
 * al cerrar la transacción de negocio, no antes).
 */
@SpringBootTest
class VentaRollbackIntegracionTest {

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
    private Long usuarioId;

    @BeforeEach
    void asegurarCajaAbierta() {
        boticaId = contexto.boticaId();
        usuarioId = contexto.usuarioId();
        if (cajaDao.buscarAbiertaDelUsuario(boticaId, usuarioId).isEmpty()) {
            cajaDao.insertar(CajaDiaria.builder()
                    .boticaId(boticaId).usuarioId(usuarioId).fecha(fechaNegocio.hoy()).turno(contexto.turno())
                    .montoApertura(new BigDecimal("100.00")).horaApertura(fechaNegocio.ahora())
                    .build());
        }
    }

    @Test
    void ventaDeDosLineas_laSegundaSinStock_dejaElStockDeAMBAS_exactamenteIgualQueAntes() {
        // Línea 1: producto con stock de sobra -- si la transacción estuviera mal delimitada,
        // esta línea se procesaría y descontaría ANTES de que la línea 2 falle.
        long[] linea1 = productoPresentacionYLoteConStock(500);
        // Línea 2: un producto DISTINTO, con stock deliberadamente insuficiente para lo que se pide.
        long[] linea2 = productoPresentacionYLoteConStock(2);

        int stockLinea1Antes = stockDelLote(linea1[2]);
        int stockLinea2Antes = stockDelLote(linea2[2]);

        UUID clave = UUID.randomUUID();
        NuevaVentaRequest pedido = new NuevaVentaRequest(clave, List.of(
                new ItemVentaRequest(linea1[0], linea1[1], 5, "BUSQUEDA"), // pedido normal, stock alcanza
                new ItemVentaRequest(linea2[0], linea2[1], 10, "BUSQUEDA") // pide 10, solo hay 2 -- esta falla
        ), "efectivo");

        assertThatThrownBy(() -> ventaService.registrar(pedido)).isInstanceOf(StockInsuficienteException.class);

        assertThat(stockDelLote(linea1[2]))
                .as("la línea 1 (con stock de sobra) NO debe haber quedado descontada -- el rollback es de TODA la venta")
                .isEqualTo(stockLinea1Antes);
        assertThat(stockDelLote(linea2[2])).isEqualTo(stockLinea2Antes);

        Long ventasConEsaClave = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ventas WHERE clave_idempotencia = ?", Long.class, clave);
        assertThat(ventasConEsaClave).as("ninguna fila de venta a medias, ni cabecera ni detalle").isZero();
    }

    /** productoId, presentacionId ("Unidad", factor 1), loteId -- todos nuevos, aislados del resto del seed. */
    private long[] productoPresentacionYLoteConStock(int stock) {
        String codigoBarras = "QA-" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre) VALUES (?, ?, ?, ?, ?)",
                boticaId, "Producto rollback QA", "Otros", codigoBarras, "unidad");
        Long productoId = jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? AND codigo_barras = ?", Long.class, boticaId, codigoBarras);

        jdbcTemplate.update(
                "INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) VALUES (?, ?, 'Unidad', 1, 5.00)",
                boticaId, productoId);
        Long presentacionId = jdbcTemplate.queryForObject(
                "SELECT id FROM presentaciones WHERE producto_id = ?", Long.class, productoId);

        String codigoLote = "QA-L-" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                        "VALUES (?, ?, ?, CURRENT_DATE + INTERVAL '1 year', ?, 1.00)",
                boticaId, productoId, codigoLote, stock);
        Long loteId = jdbcTemplate.queryForObject(
                "SELECT id FROM lotes WHERE producto_id = ? AND codigo = ?", Long.class, productoId, codigoLote);

        return new long[]{productoId, presentacionId, loteId};
    }

    private int stockDelLote(long loteId) {
        return jdbcTemplate.queryForObject("SELECT stock FROM lotes WHERE id = ?", Integer.class, loteId);
    }
}
