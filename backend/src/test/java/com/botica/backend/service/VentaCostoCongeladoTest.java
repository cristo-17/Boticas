package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 13, sección H (D2, docs/DECISIONES.md): venta_detalle.costo_unitario
 * se captura una vez, al vender, desde el lote bloqueado -- nunca se
 * vuelve a leer con un JOIN. Si un lote se repone después a un costo
 * distinto (compra nueva, mismo lote físico corregido), las ventas ya
 * registradas no deben "verlo": ese costo viejo es lo único que
 * reconstruye la rentabilidad real del momento de la venta.
 * @Transactional: rollback automático, no deja filas en botica_test.
 */
@SpringBootTest
@Transactional
class VentaCostoCongeladoTest {

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
    void ventaDetalle_costoUnitario_noCambiaAunqueElCostoDelLoteSeActualiceDespues() {
        BigDecimal costoOriginal = new BigDecimal("3.20");
        long[] ids = productoPresentacionYLote(costoOriginal, 50); // productoId, presentacionId, loteId

        var respuesta = ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(),
                List.of(new ItemVentaRequest(ids[0], ids[1], 2, "BUSQUEDA")), "efectivo"));

        BigDecimal costoCongeladoJustoTrasLaVenta = costoUnitarioDeLaVenta(respuesta.id());
        assertThat(costoCongeladoJustoTrasLaVenta).isEqualByComparingTo(costoOriginal);

        // Simula una reposición posterior del MISMO lote a un costo distinto -- p. ej. una
        // corrección del dato de compra. La venta ya registrada no debe enterarse.
        BigDecimal costoNuevo = new BigDecimal("5.75");
        jdbcTemplate.update("UPDATE lotes SET costo_unitario = ? WHERE id = ?", costoNuevo, ids[2]);

        BigDecimal costoTrasLaActualizacionDelLote = costoUnitarioDeLaVenta(respuesta.id());
        assertThat(costoTrasLaActualizacionDelLote)
                .as("costo_unitario de venta_detalle está congelado al momento de la venta, no es un JOIN en vivo a lotes")
                .isEqualByComparingTo(costoOriginal)
                .isNotEqualByComparingTo(costoNuevo);
    }

    private BigDecimal costoUnitarioDeLaVenta(Long ventaId) {
        return jdbcTemplate.queryForObject(
                "SELECT costo_unitario FROM venta_detalle WHERE venta_id = ?", BigDecimal.class, ventaId);
    }

    private long[] productoPresentacionYLote(BigDecimal costoUnitario, int stock) {
        String codigoBarras = "QAH-" + (System.nanoTime() % 1_000_000_000_000L);
        jdbcTemplate.update(
                "INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre) VALUES (?, ?, ?, ?, ?)",
                boticaId, "Producto costo congelado QA", "Otros", codigoBarras, "unidad");
        Long productoId = jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? AND codigo_barras = ?", Long.class, boticaId, codigoBarras);

        jdbcTemplate.update(
                "INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) VALUES (?, ?, 'Unidad', 1, 8.00)",
                boticaId, productoId);
        Long presentacionId = jdbcTemplate.queryForObject(
                "SELECT id FROM presentaciones WHERE producto_id = ?", Long.class, productoId);

        String codigoLote = "QAH-L-" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                        "VALUES (?, ?, ?, CURRENT_DATE + INTERVAL '1 year', ?, ?)",
                boticaId, productoId, codigoLote, stock, costoUnitario);
        Long loteId = jdbcTemplate.queryForObject(
                "SELECT id FROM lotes WHERE producto_id = ? AND codigo = ?", Long.class, productoId, codigoLote);

        return new long[]{productoId, presentacionId, loteId};
    }
}
