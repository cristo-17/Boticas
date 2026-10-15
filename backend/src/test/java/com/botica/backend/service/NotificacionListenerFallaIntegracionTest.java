package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dao.NotificacionDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * T15 V1, pregunta 3: si guardar la notificación falla (base caída a
 * medias, constraint nueva, lo que sea), la venta ya cobrada NO se cae ni
 * responde error -- el aviso es secundario, el cobro no. Clase aparte
 * porque el @MockitoBean levanta su propio contexto de Spring.
 */
@SpringBootTest
class NotificacionListenerFallaIntegracionTest {

    @MockitoBean
    private NotificacionDao notificacionDao;
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

    @BeforeEach
    void asegurarCajaAbierta() {
        if (cajaDao.buscarAbiertaDelUsuario(contexto.boticaId(), contexto.usuarioId()).isEmpty()) {
            cajaDao.insertar(CajaDiaria.builder()
                    .boticaId(contexto.boticaId()).usuarioId(contexto.usuarioId()).fecha(fechaNegocio.hoy())
                    .turno(contexto.turno()).montoApertura(new BigDecimal("100.00")).horaApertura(fechaNegocio.ahora())
                    .build());
        }
    }

    @Test
    void listenerQueFalla_noTumbaLaVenta_yLaVentaQuedaGuardada() {
        when(notificacionDao.insertar(any())).thenThrow(new IllegalStateException("simulado: no se pudo guardar el aviso"));
        Long boticaId = contexto.boticaId();
        String sufijo = String.valueOf(System.nanoTime() % 1_000_000_000_000L); // codigo_barras es VARCHAR(20)
        jdbcTemplate.update(
                "INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre) VALUES (?, ?, 'Otros', ?, 'unidad')",
                boticaId, "Producto falla QA " + sufijo, "QA-FA-" + sufijo);
        Long productoId = jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? AND codigo_barras = ?", Long.class, boticaId, "QA-FA-" + sufijo);
        jdbcTemplate.update(
                "INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) VALUES (?, ?, 'Unidad', 1, 5.00)",
                boticaId, productoId);
        Long presentacionId = jdbcTemplate.queryForObject("SELECT id FROM presentaciones WHERE producto_id = ?", Long.class, productoId);
        jdbcTemplate.update(
                "INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                        "VALUES (?, ?, ?, CURRENT_DATE + INTERVAL '1 year', 12, 1.00)",
                boticaId, productoId, "QA-FA-L-" + sufijo);

        UUID clave = UUID.randomUUID();
        // 12 - 2 = 10 <= umbral: dispara el evento, y el listener revienta.
        ventaService.registrar(new NuevaVentaRequest(clave, List.of(
                new ItemVentaRequest(productoId, presentacionId, 2, "BUSQUEDA")), "efectivo"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ventas WHERE clave_idempotencia = ?", Long.class, clave))
                .isEqualTo(1);
    }
}
