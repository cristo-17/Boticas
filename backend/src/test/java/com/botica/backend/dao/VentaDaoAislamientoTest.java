package com.botica.backend.dao;

import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.model.Venta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-E2-2: el TECNICO ve solo las ventas de su caja actual. El filtro de caja
 * vive en el SQL (no en memoria), así que se prueba contra Postgres real:
 * el contenido Y el total de la página deben ser solo los de esa caja, y una
 * caja de otra botica nunca devuelve nada.
 * @Transactional: cada test deja botica_test como estaba.
 */
@SpringBootTest
@Transactional
class VentaDaoAislamientoTest {

    @Autowired
    private VentaDao ventaDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long sanLucas;
    private Long vidaSana;
    private Long cajaA;
    private Long cajaB;
    private Long cajaDeVidaSana;
    private Long ventaEnA;
    private Long ventaEnB;
    private Long ventaEnVidaSana;

    @BeforeEach
    void prepararDosCajasConVentas() {
        sanLucas = jdbcTemplate.queryForObject("SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'", Long.class);
        vidaSana = jdbcTemplate.queryForObject("SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'", Long.class);

        cajaA = cajaCerrada(sanLucas);
        cajaB = cajaCerrada(sanLucas);
        cajaDeVidaSana = cajaCerrada(vidaSana);
        ventaEnA = venta(sanLucas, cajaA);
        ventaEnB = venta(sanLucas, cajaB);
        ventaEnVidaSana = venta(vidaSana, cajaDeVidaSana);
    }

    @Test
    void listarPaginado_conCaja_soloDevuelveLasVentasDeEsaCaja_yElTotalCoincide() {
        PaginaResponse<Venta> pagina = ventaDao.listarPaginado(sanLucas, cajaA, 0, 100, "fecha,desc");

        assertThat(pagina.contenido()).extracting(Venta::getId).containsExactly(ventaEnA);
        assertThat(pagina.totalElementos()).as("el COUNT también filtra por caja, si no la paginación miente").isEqualTo(1L);
    }

    @Test
    void listarPaginado_sinCaja_devuelveTodaLaBotica_peroNuncaOtraBotica() {
        PaginaResponse<Venta> pagina = ventaDao.listarPaginado(sanLucas, null, 0, 100, "fecha,desc");

        assertThat(pagina.contenido()).extracting(Venta::getId).contains(ventaEnA, ventaEnB).doesNotContain(ventaEnVidaSana);
        assertThat(pagina.totalElementos()).isEqualTo(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ventas WHERE botica_id = ?", Long.class, sanLucas));
    }

    @Test
    void listarPaginado_conCajaDeOtraBotica_devuelveVacio() {
        PaginaResponse<Venta> pagina = ventaDao.listarPaginado(sanLucas, cajaDeVidaSana, 0, 100, "fecha,desc");

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
    }

    /** CERRADA para no chocar con idx_caja_abierta_por_usuario (V8: una abierta por usuario). */
    private Long cajaCerrada(Long boticaId) {
        Long usuarioId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE botica_id = ? ORDER BY id LIMIT 1", Long.class, boticaId);
        return jdbcTemplate.queryForObject(
                "INSERT INTO caja_diaria (botica_id, usuario_id, fecha, turno, monto_apertura, estado) " +
                        "VALUES (?, ?, CURRENT_DATE, 'Noche', 100.00, 'CERRADA') RETURNING id",
                Long.class, boticaId, usuarioId);
    }

    private Long venta(Long boticaId, Long cajaId) {
        Long usuarioId = jdbcTemplate.queryForObject("SELECT usuario_id FROM caja_diaria WHERE id = ?", Long.class, cajaId);
        return jdbcTemplate.queryForObject(
                "INSERT INTO ventas (botica_id, usuario_id, caja_id, subtotal, igv, igv_tasa, total, metodo_pago, clave_idempotencia) " +
                        "VALUES (?, ?, ?, 8.47, 1.53, 0.18, 10.00, 'efectivo', ?) RETURNING id",
                Long.class, boticaId, usuarioId, cajaId, UUID.randomUUID());
    }
}
