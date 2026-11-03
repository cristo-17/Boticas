package com.botica.backend.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 20, Bloque B: movimientos_stock es inmutable (trigger de V10).
 *
 * <p>Sin @Transactional a propósito: cada sentencia corre y se confirma sola,
 * igual que en producción. Si el trigger no existiera, el UPDATE o el DELETE
 * quedarían guardados de verdad, y el test lo detecta releyendo la fila.</p>
 */
@SpringBootTest
class MovimientosStockInmutableTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void updateDeUnMovimiento_falla_yLaFilaQuedaIgual() {
        Map<String, Object> antes = cualquierMovimiento();
        Object id = antes.get("id");

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE movimientos_stock SET cantidad = cantidad + 1 WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("movimientos_stock es inmutable");

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM movimientos_stock WHERE id = ?", id)).isEqualTo(antes);
    }

    @Test
    void deleteDeUnMovimiento_falla_yLaFilaSigue() {
        Object id = cualquierMovimiento().get("id");

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM movimientos_stock WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("movimientos_stock es inmutable");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM movimientos_stock WHERE id = ?", Long.class, id))
                .isEqualTo(1L);
    }

    @Test
    void truncate_falla() {
        assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE movimientos_stock"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("movimientos_stock es inmutable");
    }

    /** El ON DELETE SET NULL de referencia_venta_id es un UPDATE: borrar una venta con movimientos falla. */
    @Test
    void borrarUnaVentaConMovimientos_falla_porElSetNull() {
        Long ventaId = jdbcTemplate.queryForObject(
                "SELECT referencia_venta_id FROM movimientos_stock WHERE referencia_venta_id IS NOT NULL ORDER BY id LIMIT 1",
                Long.class);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM ventas WHERE id = ?", ventaId))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ventas WHERE id = ?", Long.class, ventaId)).isEqualTo(1L);
    }

    private Map<String, Object> cualquierMovimiento() {
        return jdbcTemplate.queryForMap("SELECT * FROM movimientos_stock ORDER BY id LIMIT 1");
    }
}
