package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dto.CatalogoOfflineResponse;
import com.botica.backend.dto.ProductoOfflineResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloque offline-fix (punto 1): todo producto ACTIVO del catálogo viaja en la foto
 * para vender sin conexión, aunque esté agotado o solo tenga lotes vencidos. Lo que
 * cambia es `lotesVendibles` (vacío) y el aviso de vencimiento/stock, nunca si aparece.
 */
@SpringBootTest
@Transactional
class CatalogoOfflineIntegracionTest {

    @Autowired
    private ProductoService productoService;
    @Autowired
    private ContextoOperacion contexto;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void agotado_soloVencido_sinLotes_yConStock_vanTodosEnLaFoto_conSusLotesVendibles() {
        String agotado = producto("Offline agotado QA", true);
        lote(agotado, 0, "CURRENT_DATE + 200");
        String vencido = producto("Offline vencido QA", true);
        lote(vencido, 40, "CURRENT_DATE - 1");
        String sinLotes = producto("Offline sin lotes QA", true);
        String conStock = producto("Offline con stock QA", true);
        lote(conStock, 12, "CURRENT_DATE + 30");
        lote(conStock, 5, "CURRENT_DATE - 3");

        CatalogoOfflineResponse foto = productoService.catalogoOffline();

        assertThat(enFoto(foto, agotado)).hasValueSatisfying(p -> {
            assertThat(p.lotesVendibles()).as("agotado: sin lotes vendibles, pero está").isEmpty();
            assertThat(p.presentaciones()).isNotEmpty();
        });
        assertThat(enFoto(foto, vencido)).hasValueSatisfying(p -> {
            assertThat(p.lotesVendibles()).as("solo vencido: sin lotes vendibles").isEmpty();
            assertThat(p.estadoVencimiento()).as("mismo aviso que en línea: sin stock vendible").isNull();
        });
        assertThat(enFoto(foto, sinLotes)).hasValueSatisfying(p -> assertThat(p.lotesVendibles()).isEmpty());
        assertThat(enFoto(foto, conStock)).hasValueSatisfying(p -> {
            assertThat(p.lotesVendibles()).singleElement().satisfies(l -> assertThat(l.stock()).isEqualTo(12));
        });
    }

    @Test
    void unProductoDesactivado_noVaEnLaFoto() {
        String inactivo = producto("Offline inactivo QA", false);
        lote(inactivo, 10, "CURRENT_DATE + 100");

        assertThat(enFoto(productoService.catalogoOffline(), inactivo)).isEmpty();
    }

    private Optional<ProductoOfflineResponse> enFoto(CatalogoOfflineResponse foto, String codigo) {
        return foto.productos().stream().filter(p -> codigo.equals(p.codigoBarras())).findFirst();
    }

    private String producto(String nombre, boolean activo) {
        Long boticaId = contexto.boticaId();
        String codigo = "QA-OF-" + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        Long productoId = jdbc.queryForObject(
                "INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre, requiere_receta, activo) " +
                        "VALUES (?, ?, 'Otros', ?, 'unidad', false, ?) RETURNING id",
                Long.class, boticaId, nombre, codigo, activo);
        jdbc.update("INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) VALUES (?, ?, 'Unidad', 1, ?)",
                boticaId, productoId, new BigDecimal("2.50"));
        return codigo;
    }

    private void lote(String codigoProducto, int stock, String vencimientoSql) {
        jdbc.update("INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                        "SELECT botica_id, id, ?, " + vencimientoSql + ", ?, 1.00 FROM productos WHERE codigo_barras = ? AND botica_id = ?",
                "L-" + codigoProducto + "-" + stock + "-" + vencimientoSql.hashCode(), stock, codigoProducto, contexto.boticaId());
    }
}
