package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaMermaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.NuevoLoteRequest;
import com.botica.backend.dto.PresentacionCatalogoResponse;
import com.botica.backend.dto.PresentacionRequest;
import com.botica.backend.dto.ProductoCatalogoResponse;
import com.botica.backend.dto.ProductoRequest;
import com.botica.backend.dto.ProductoResponse;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.exception.PresentacionInvalidaException;
import com.botica.backend.exception.ProductoInactivoException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 17, Bloque A, contra la base real (botica_test): lo que un mock no
 * puede probar — el precio congelado en venta_detalle, el FK RESTRICT de una
 * presentación con ventas y que un producto desactivado siga pudiendo
 * retirarse por merma. @Transactional: rollback, no deja filas.
 */
@SpringBootTest
@Transactional
class CatalogoProductoIntegracionTest {

    @Autowired
    private CatalogoProductoService catalogoService;
    @Autowired
    private ProductoService productoService;
    @Autowired
    private VentaService ventaService;
    @Autowired
    private LoteService loteService;
    @Autowired
    private MermaService mermaService;
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
                    .boticaId(contexto.boticaId()).usuarioId(contexto.usuarioId()).fecha(fechaNegocio.hoy()).turno(contexto.turno())
                    .montoApertura(new BigDecimal("100.00")).horaApertura(fechaNegocio.ahora())
                    .build());
        }
    }

    @Test
    void cambiarElPrecio_soloAfectaVentasFuturas_lasPasadasConservanElSuyo() {
        ProductoCatalogoResponse creado = productoConTresPresentaciones();
        Long loteId = loteCon(creado.id(), 500);
        PresentacionCatalogoResponse unidad = presentacion(creado, 1);

        VentaResponse primera = vender(creado.id(), unidad.id(), 3);

        catalogoService.actualizar(creado.id(), requestDesde(creado, unidad.id(), new BigDecimal("0.35")));
        VentaResponse segunda = vender(creado.id(), unidad.id(), 3);

        assertThat(precioDeLaVenta(primera.id())).as("la venta pasada no ve el precio nuevo").isEqualByComparingTo("0.20");
        assertThat(totalDeLaVenta(primera.id())).isEqualByComparingTo("0.60");
        assertThat(precioDeLaVenta(segunda.id())).as("la venta nueva sí").isEqualByComparingTo("0.35");
        assertThat(totalDeLaVenta(segunda.id())).isEqualByComparingTo("1.05");
        assertThat(loteId).isNotNull();
    }

    @Test
    void quitarUnaPresentacionQueYaTieneVentas_laDesactivaSinRomperLaVentaYDejaDeVenderse() {
        ProductoCatalogoResponse creado = productoConTresPresentaciones();
        loteCon(creado.id(), 500);
        PresentacionCatalogoResponse blister = presentacion(creado, 10);
        VentaResponse venta = vender(creado.id(), blister.id(), 2);

        // Se reenvían todas menos el blíster.
        List<PresentacionRequest> sinBlister = creado.presentaciones().stream()
                .filter(p -> !p.id().equals(blister.id()))
                .map(p -> new PresentacionRequest(p.id(), p.etiqueta(), p.factorConversion(), p.precio()))
                .toList();
        ProductoCatalogoResponse editado = catalogoService.actualizar(creado.id(), requestCon(creado, sinBlister));

        assertThat(presentacion(editado, 10).activo()).as("sigue existiendo, inactiva").isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM venta_detalle WHERE venta_id = ? AND presentacion_id = ?", Integer.class, venta.id(), blister.id()))
                .as("la venta pasada sigue apuntando a esa presentación").isEqualTo(1);
        assertThatThrownBy(() -> vender(creado.id(), blister.id(), 1)).isInstanceOf(PresentacionInvalidaException.class);
        ProductoResponse enMostrador = productoService.buscar(creado.codigoBarras(), false).get(0);
        assertThat(enMostrador.presentaciones()).extracting("factorConversion").containsExactly(100, 1);
    }

    @Test
    void productoDesactivado_noSeVendeNiRecibeLotes_peroSigueEnMermaEInventario() {
        ProductoCatalogoResponse creado = productoConTresPresentaciones();
        Long loteId = loteCon(creado.id(), 40);
        PresentacionCatalogoResponse unidad = presentacion(creado, 1);

        catalogoService.cambiarEstado(creado.id(), false);

        // Punto de Venta y alta de lote
        assertThat(productoService.buscar(creado.codigoBarras(), false)).isEmpty();
        assertThatThrownBy(() -> productoService.buscarPorCodigoBarras(creado.codigoBarras()))
                .isInstanceOf(ProductoInactivoException.class);
        assertThatThrownBy(() -> vender(creado.id(), unidad.id(), 1)).isInstanceOf(ProductoInactivoException.class);
        assertThatThrownBy(() -> loteCon(creado.id(), 10)).isInstanceOf(ProductoInactivoException.class);

        // Merma: la misma búsqueda con incluirInactivos, y el retiro de su stock
        List<ProductoResponse> paraMerma = productoService.buscar(creado.codigoBarras(), true);
        assertThat(paraMerma).singleElement().satisfies(p -> assertThat(p.activo()).isFalse());
        mermaService.registrar(new NuevaMermaRequest(loteId, 5, "Vencimiento", null));
        assertThat(jdbcTemplate.queryForObject("SELECT stock FROM lotes WHERE id = ?", Integer.class, loteId)).isEqualTo(35);

        // Inventario
        assertThat(loteService.listar(null, "todos", false, creado.id(), 0, 20, null).contenido())
                .extracting("id").containsExactly(loteId);

        // Reactivar lo devuelve al mostrador
        catalogoService.cambiarEstado(creado.id(), true);
        assertThat(productoService.buscar(creado.codigoBarras(), false)).hasSize(1);
    }

    // --- ayudas --------------------------------------------------------------

    private ProductoCatalogoResponse productoConTresPresentaciones() {
        String codigo = "QA17-" + (System.nanoTime() % 1_000_000_000_000L);
        return catalogoService.crear(new ProductoRequest("Producto catálogo QA", "Lab QA", "Otros", codigo, "tableta", List.of(
                new PresentacionRequest(null, "Caja", 100, new BigDecimal("15.00")),
                new PresentacionRequest(null, "Blíster", 10, new BigDecimal("1.80")),
                new PresentacionRequest(null, "Unidad", 1, new BigDecimal("0.20")))));
    }

    private static PresentacionCatalogoResponse presentacion(ProductoCatalogoResponse producto, int factor) {
        return producto.presentaciones().stream().filter(p -> p.factorConversion() == factor).findFirst().orElseThrow();
    }

    /** Reenvía todas las presentaciones tal cual, salvo el precio de una. */
    private static ProductoRequest requestDesde(ProductoCatalogoResponse producto, Long presentacionId, BigDecimal precioNuevo) {
        List<PresentacionRequest> todas = producto.presentaciones().stream()
                .map(p -> new PresentacionRequest(p.id(), p.etiqueta(), p.factorConversion(),
                        p.id().equals(presentacionId) ? precioNuevo : p.precio()))
                .toList();
        return requestCon(producto, todas);
    }

    private static ProductoRequest requestCon(ProductoCatalogoResponse producto, List<PresentacionRequest> presentaciones) {
        return new ProductoRequest(producto.nombre(), producto.laboratorio(), producto.categoria(),
                producto.codigoBarras(), producto.unidadNombre(), presentaciones);
    }

    private Long loteCon(Long productoId, int stock) {
        return loteService.registrar(new NuevoLoteRequest(productoId, "QA17-L-" + System.nanoTime(),
                fechaNegocio.hoy().plusYears(1), stock, null, new BigDecimal("0.10"))).id();
    }

    private VentaResponse vender(Long productoId, Long presentacionId, int cantidad) {
        return ventaService.registrar(new NuevaVentaRequest(UUID.randomUUID(),
                List.of(new ItemVentaRequest(productoId, presentacionId, cantidad, "BUSQUEDA")), "efectivo"));
    }

    private BigDecimal precioDeLaVenta(Long ventaId) {
        return jdbcTemplate.queryForObject("SELECT precio_unitario FROM venta_detalle WHERE venta_id = ?", BigDecimal.class, ventaId);
    }

    private BigDecimal totalDeLaVenta(Long ventaId) {
        return jdbcTemplate.queryForObject("SELECT total_linea FROM venta_detalle WHERE venta_id = ?", BigDecimal.class, ventaId);
    }
}
