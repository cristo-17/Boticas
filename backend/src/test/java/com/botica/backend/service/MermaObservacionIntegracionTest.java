package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dto.MermaResponse;
import com.botica.backend.dto.NuevaMermaRequest;
import com.botica.backend.exception.ObservacionRequeridaException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF-28 con la configuración real (ConfigService, la misma de GET /api/config): "Robo o pérdida"
 * y "Otro" exigen observación; sin ella no se registra nada ni se mueve el stock.
 */
@SpringBootTest
@Transactional
class MermaObservacionIntegracionTest {

    @Autowired
    private MermaService mermaService;
    @Autowired
    private ContextoOperacion contexto;
    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"Robo o pérdida", "Otro"})
    void sinObservacion_seRechaza_yElStockNoCambia(String motivo) {
        Long loteId = loteConStock(10);
        long mermasAntes = jdbc.queryForObject("SELECT count(*) FROM mermas WHERE lote_id = ?", Long.class, loteId);

        assertThatThrownBy(() -> mermaService.registrar(new NuevaMermaRequest(loteId, 2, motivo, null)))
                .isInstanceOf(ObservacionRequeridaException.class);
        assertThatThrownBy(() -> mermaService.registrar(new NuevaMermaRequest(loteId, 2, motivo, "   ")))
                .isInstanceOf(ObservacionRequeridaException.class);

        assertThat(jdbc.queryForObject("SELECT stock FROM lotes WHERE id = ?", Integer.class, loteId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mermas WHERE lote_id = ?", Long.class, loteId)).isEqualTo(mermasAntes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Robo o pérdida", "Otro"})
    void conObservacion_seRegistra_yDescuentaElStock(String motivo) {
        Long loteId = loteConStock(10);

        MermaResponse merma = mermaService.registrar(new NuevaMermaRequest(loteId, 2, motivo, "Faltaron 2 en el conteo"));

        assertThat(merma.motivo()).isEqualTo(motivo);
        assertThat(merma.observacion()).isEqualTo("Faltaron 2 en el conteo");
        assertThat(jdbc.queryForObject("SELECT stock FROM lotes WHERE id = ?", Integer.class, loteId)).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Vencimiento", "Rotura", "Deterioro"})
    void losDemasMotivos_noExigenObservacion(String motivo) {
        Long loteId = loteConStock(10);
        assertThat(mermaService.registrar(new NuevaMermaRequest(loteId, 1, motivo, null)).motivo()).isEqualTo(motivo);
    }

    private Long loteConStock(int stock) {
        Long boticaId = contexto.boticaId();
        String codigo = "QA-MO-" + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        Long productoId = jdbc.queryForObject("INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre, requiere_receta) " +
                "VALUES (?, 'Merma QA', 'Otros', ?, 'unidad', false) RETURNING id", Long.class, boticaId, codigo);
        jdbc.update("INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) VALUES (?, ?, 'Unidad', 1, ?)",
                boticaId, productoId, new BigDecimal("3.00"));
        return jdbc.queryForObject("INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                "VALUES (?, ?, ?, CURRENT_DATE + 200, ?, 1.00) RETURNING id", Long.class, boticaId, productoId, "L-" + codigo, stock);
    }
}
