package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.RecetaRequest;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.exception.NegocioException;
import com.botica.backend.exception.RecetaInvalidaException;
import com.botica.backend.exception.RecetaRequeridaException;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 20, Bloque A (D-E2-4): el servidor exige la receta si al menos una
 * línea es de un producto con requiereReceta. No basta con la pantalla.
 */
@SpringBootTest
@Transactional
class VentaRecetaIntegracionTest {

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

    private long[] antibiotico;
    private long[] vitamina;

    @BeforeEach
    void preparar() {
        Long boticaId = contexto.boticaId();
        if (cajaDao.buscarAbiertaDelUsuario(boticaId, contexto.usuarioId()).isEmpty()) {
            cajaDao.insertar(CajaDiaria.builder()
                    .boticaId(boticaId).usuarioId(contexto.usuarioId()).fecha(fechaNegocio.hoy()).turno(contexto.turno())
                    .montoApertura(new BigDecimal("100.00")).horaApertura(fechaNegocio.ahora())
                    .build());
        }
        antibiotico = productoConLote("Cefalexina receta QA", true);
        vitamina = productoConLote("Complejo B receta QA", false);
    }

    @Test
    void lineaQueRequiereReceta_sinReceta_400RecetaRequerida() {
        NuevaVentaRequest pedido = new NuevaVentaRequest(UUID.randomUUID(), List.of(
                item(vitamina, 1), item(antibiotico, 2)), "efectivo", null);

        assertThatThrownBy(() -> ventaService.registrar(pedido))
                .isInstanceOf(RecetaRequeridaException.class)
                .hasMessageContaining("Cefalexina receta QA")
                .satisfies(e -> {
                    assertThat(((NegocioException) e).getCodigo()).isEqualTo("RECETA_REQUERIDA");
                    assertThat(((NegocioException) e).getStatus().value()).isEqualTo(400);
                });
    }

    @Test
    void fechaDeEmisionFutura_400RecetaInvalida() {
        NuevaVentaRequest pedido = new NuevaVentaRequest(UUID.randomUUID(), List.of(item(antibiotico, 1)), "efectivo",
                new RecetaRequest("R-9", "4321", null, fechaNegocio.hoy().plusDays(1)));

        assertThatThrownBy(() -> ventaService.registrar(pedido)).isInstanceOf(RecetaInvalidaException.class);
    }

    @Test
    void conReceta_seGuardaConLaVenta_yLaDevuelveLaConsulta() {
        VentaResponse venta = ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(
                item(antibiotico, 2), item(vitamina, 1)), "efectivo",
                new RecetaRequest("  RX-2026-15 ", " 54321 ", "  ", fechaNegocio.hoy())));

        assertThat(venta.receta()).isNotNull();
        assertThat(venta.receta().numero()).isEqualTo("RX-2026-15");
        assertThat(venta.receta().cmp()).isEqualTo("54321");
        assertThat(venta.receta().medicoNombre()).as("en blanco = sin dato").isNull();

        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT botica_id, numero, cmp, creado_por FROM recetas WHERE venta_id = ?", venta.id());
        assertThat(fila).containsEntry("numero", "RX-2026-15").containsEntry("cmp", "54321")
                .containsEntry("creado_por", contexto.usuarioId()).containsEntry("botica_id", contexto.boticaId());

        assertThat(ventaService.obtenerPorId(venta.id()).receta().numero()).isEqualTo("RX-2026-15");
    }

    @Test
    void sinLineasQueLaRequieran_laRecetaQueLlegueSeIgnora() {
        VentaResponse venta = ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(), List.of(item(vitamina, 1)),
                "efectivo", new RecetaRequest("R-1", "1", null, null)));

        assertThat(venta.receta()).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM recetas WHERE venta_id = ?", Long.class, venta.id()))
                .isZero();
    }

    private static ItemVentaRequest item(long[] producto, int cantidad) {
        return new ItemVentaRequest(producto[0], producto[1], cantidad, "BUSQUEDA");
    }

    private long[] productoConLote(String nombre, boolean requiereReceta) {
        Long boticaId = contexto.boticaId();
        String codigo = "QA-R-" + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        jdbcTemplate.update("INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre, requiere_receta) " +
                "VALUES (?, ?, 'Otros', ?, 'unidad', ?)", boticaId, nombre, codigo, requiereReceta);
        Long productoId = jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? AND codigo_barras = ?", Long.class, boticaId, codigo);
        jdbcTemplate.update("INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) " +
                "VALUES (?, ?, 'Unidad', 1, 3.00)", boticaId, productoId);
        Long presentacionId = jdbcTemplate.queryForObject(
                "SELECT id FROM presentaciones WHERE producto_id = ?", Long.class, productoId);
        jdbcTemplate.update("INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                "VALUES (?, ?, ?, CURRENT_DATE + 365, 50, 1.00)", boticaId, productoId, "L-" + codigo);
        return new long[]{productoId, presentacionId};
    }
}
