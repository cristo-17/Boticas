package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.VentaResponse;
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

/**
 * RF-21: una venta deja sus movimientos de stock (uno por lote que tocó FEFO, cantidad negativa,
 * con la venta, el origen y el usuario) y UN movimiento de caja, que solo afecta el efectivo si se
 * pagó en efectivo. Se miran las filas, no solo el cierre.
 */
@SpringBootTest
@Transactional
class VentaMovimientosIntegracionTest {

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
    void ventaEnEfectivoRepartidaEnDosLotes_dejaUnMovimientoDeStockPorLote_yUnoDeCajaQueAfectaElEfectivo() {
        long[] p = producto();
        Long loteViejo = lote(p[0], 3, "CURRENT_DATE + 20");
        Long loteNuevo = lote(p[0], 10, "CURRENT_DATE + 300");
        long cajaAntes = movimientosCaja();
        BigDecimal efectivoAntes = cajaDao.sumarMovimientosEfectivo(contexto.boticaId(), cajaId);

        VentaResponse venta = ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(),
                List.of(new ItemVentaRequest(p[0], p[1], 5, "ESCANEO")), "efectivo", null));

        List<Map<String, Object>> stock = jdbc.queryForList(
                "SELECT lote_id, tipo, cantidad, origen_captura, creado_por FROM movimientos_stock WHERE referencia_venta_id = ? ORDER BY cantidad",
                venta.id());
        assertThat(stock).hasSize(2);
        assertThat(stock.get(0)).containsEntry("lote_id", loteViejo).containsEntry("tipo", "VENTA").containsEntry("cantidad", -3)
                .containsEntry("origen_captura", "ESCANEO").containsEntry("creado_por", contexto.usuarioId());
        assertThat(stock.get(1)).containsEntry("lote_id", loteNuevo).containsEntry("cantidad", -2);

        assertThat(movimientosCaja()).isEqualTo(cajaAntes + 1);
        Map<String, Object> caja = ultimoMovimientoCaja();
        assertThat(caja.get("tipo")).isEqualTo("venta");
        assertThat((BigDecimal) caja.get("monto")).isEqualByComparingTo(venta.total());
        assertThat(caja.get("afecta_efectivo")).isEqualTo(true);
        assertThat(cajaDao.sumarMovimientosEfectivo(contexto.boticaId(), cajaId))
                .isEqualByComparingTo(efectivoAntes.add(venta.total()));
    }

    @Test
    void ventaConYape_dejaSuMovimientoDeCaja_peroNoSumaAlEfectivo() {
        long[] p = producto();
        lote(p[0], 10, "CURRENT_DATE + 100");
        BigDecimal efectivoAntes = cajaDao.sumarMovimientosEfectivo(contexto.boticaId(), cajaId);

        VentaResponse venta = ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(),
                List.of(new ItemVentaRequest(p[0], p[1], 2, "BUSQUEDA")), "yape", null));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimientos_stock WHERE referencia_venta_id = ?", Long.class, venta.id()))
                .isEqualTo(1L);
        Map<String, Object> caja = ultimoMovimientoCaja();
        assertThat((BigDecimal) caja.get("monto")).isEqualByComparingTo(venta.total());
        assertThat(caja.get("afecta_efectivo")).isEqualTo(false);
        assertThat(cajaDao.sumarMovimientosEfectivo(contexto.boticaId(), cajaId)).isEqualByComparingTo(efectivoAntes);
    }

    private long movimientosCaja() {
        return jdbc.queryForObject("SELECT count(*) FROM movimientos_caja WHERE caja_id = ?", Long.class, cajaId);
    }

    private Map<String, Object> ultimoMovimientoCaja() {
        return jdbc.queryForMap("SELECT tipo, monto, afecta_efectivo FROM movimientos_caja WHERE caja_id = ? ORDER BY id DESC LIMIT 1", cajaId);
    }

    private long[] producto() {
        Long boticaId = contexto.boticaId();
        String codigo = "QA-MV-" + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        Long productoId = jdbc.queryForObject("INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre, requiere_receta) " +
                "VALUES (?, 'Movimientos QA', 'Otros', ?, 'unidad', false) RETURNING id", Long.class, boticaId, codigo);
        Long presentacionId = jdbc.queryForObject("INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) " +
                "VALUES (?, ?, 'Unidad', 1, 2.50) RETURNING id", Long.class, boticaId, productoId);
        return new long[]{productoId, presentacionId};
    }

    private Long lote(long productoId, int stock, String vencimientoSql) {
        return jdbc.queryForObject("INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                        "VALUES (?, ?, ?, " + vencimientoSql + ", ?, 1.00) RETURNING id", Long.class,
                contexto.boticaId(), productoId, "L-" + productoId + "-" + stock + "-" + vencimientoSql.hashCode(), stock);
    }
}
