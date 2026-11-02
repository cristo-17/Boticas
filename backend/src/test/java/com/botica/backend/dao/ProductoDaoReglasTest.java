package com.botica.backend.dao;

import com.botica.backend.model.Producto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 0B: búsqueda por código exacta (DIF-04) y ventana de 30 días de
 * "más vendidos" (DIF-06), contra la BD real. hoy es fijo; las ventas del
 * seed caen fuera o con cantidades mínimas, así que no interfieren.
 * @Transactional: rollback automático.
 */
@SpringBootTest
@Transactional
class ProductoDaoReglasTest {

    private static final LocalDate HOY = LocalDate.of(2026, 1, 15);
    private static final LocalDate DESDE = HOY.minusDays(29);

    @Autowired
    private ProductoDao productoDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long boticaId;
    private Long usuarioId;
    private Long cajaId;

    @BeforeEach
    void prepararBoticaUsuarioYCaja() {
        boticaId = jdbcTemplate.queryForObject("SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'", Long.class);
        usuarioId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE botica_id = ? ORDER BY id LIMIT 1", Long.class, boticaId);
        // CERRADA: no choca con la caja abierta del usuario (V8).
        cajaId = jdbcTemplate.queryForObject(
                "INSERT INTO caja_diaria (botica_id, usuario_id, fecha, turno, monto_apertura, estado) " +
                        "VALUES (?, ?, ?, 'Tarde', 0, 'CERRADA') RETURNING id",
                Long.class, boticaId, usuarioId, HOY);
    }

    @Test
    void buscarPorCodigoBarras_conUnPrefijoDelCodigo_noEncuentraNada() {
        // DIF-04: con la cámara, un prefijo leído a medias no puede devolver otro medicamento.
        String codigo = "QA" + (System.nanoTime() % 1_000_000_000_000L);
        insertarProducto("QA codigo exacto", codigo);

        assertThat(productoDao.buscarPorCodigoBarras(boticaId, codigo)).isPresent();
        assertThat(productoDao.buscarPorCodigoBarras(boticaId, codigo.substring(0, codigo.length() - 2))).isEmpty();
    }

    @Test
    void masVendidos_soloCuentaLasVentasDeLosUltimos30Dias() {
        long viejo = insertarProductoConVenta("QA mas vendido viejo", 1_000_000, HOY.minusDays(30));
        long reciente = insertarProductoConVenta("QA mas vendido reciente", 900_000, DESDE);

        List<Long> ids = productoDao.masVendidos(boticaId, 6, DESDE).stream().map(Producto::getId).toList();

        assertThat(ids).as("vendido el primer día de la ventana: cuenta").contains(reciente);
        assertThat(ids).as("vendido un día antes de la ventana: no cuenta, aunque sea el que más unidades tiene").doesNotContain(viejo);
    }

    private long insertarProducto(String nombre, String codigoBarras) {
        jdbcTemplate.update(
                "INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre) VALUES (?, ?, ?, ?, ?)",
                boticaId, nombre, "Otros", codigoBarras, "unidad");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? AND codigo_barras = ?", Long.class, boticaId, codigoBarras);
    }

    private long insertarProductoConVenta(String nombre, int cantidad, LocalDate diaDeVenta) {
        long productoId = insertarProducto(nombre, "QA" + (System.nanoTime() % 1_000_000_000_000L));
        Long presentacionId = jdbcTemplate.queryForObject(
                "INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) " +
                        "VALUES (?, ?, 'Unidad', 1, 1.00) RETURNING id", Long.class, boticaId, productoId);
        Long loteId = jdbcTemplate.queryForObject(
                "INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                        "VALUES (?, ?, ?, ?, 0, 0.50) RETURNING id",
                Long.class, boticaId, productoId, "QA-MV-" + System.nanoTime(), HOY.plusYears(1));
        // Mediodía de Lima: el día de negocio no depende de la zona del servidor.
        OffsetDateTime fecha = diaDeVenta.atTime(LocalTime.NOON).atOffset(java.time.ZoneOffset.of("-05:00"));
        Long ventaId = jdbcTemplate.queryForObject(
                "INSERT INTO ventas (botica_id, usuario_id, caja_id, fecha, subtotal, igv, igv_tasa, total, metodo_pago, clave_idempotencia) " +
                        "VALUES (?, ?, ?, ?, 0.85, 0.15, 0.18, 1.00, 'efectivo', ?) RETURNING id",
                Long.class, boticaId, usuarioId, cajaId, fecha, UUID.randomUUID());
        jdbcTemplate.update(
                "INSERT INTO venta_detalle (venta_id, botica_id, producto_id, presentacion_id, lote_id, nombre_producto, " +
                        "etiqueta_presentacion, cantidad, precio_unitario, costo_unitario, total_linea, origen_captura) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'Unidad', ?, 1.00, 0.50, 1.00, 'BUSQUEDA')",
                ventaId, boticaId, productoId, presentacionId, loteId, nombre, cantidad);
        return productoId;
    }
}
