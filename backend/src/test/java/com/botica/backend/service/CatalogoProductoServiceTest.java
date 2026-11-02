package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.ProductoDao;
import com.botica.backend.dto.PresentacionRequest;
import com.botica.backend.dto.ProductoRequest;
import com.botica.backend.exception.CodigoBarrasDuplicadoException;
import com.botica.backend.exception.FactorNoEditableException;
import com.botica.backend.exception.PresentacionDuplicadaException;
import com.botica.backend.exception.PresentacionInvalidaException;
import com.botica.backend.exception.PresentacionUnidadException;
import com.botica.backend.exception.ProductoNoEncontradoException;
import com.botica.backend.model.PresentacionProducto;
import com.botica.backend.model.Producto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas de dominio del catálogo (Tarea 17, Bloque A), sin base de datos. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogoProductoServiceTest {

    private static final Long BOTICA_ID = 1L;
    private static final Long USUARIO_ID = 7L;
    private static final Long PRODUCTO_ID = 50L;

    @Mock
    private ProductoDao productoDao;
    @Mock
    private ContextoOperacion contexto;

    private CatalogoProductoService service;

    @BeforeEach
    void configurar() {
        service = new CatalogoProductoService(productoDao, contexto);
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(productoDao.obtenerPorId(BOTICA_ID, PRODUCTO_ID)).thenReturn(Optional.of(producto()));
    }

    // --- crear ---------------------------------------------------------------

    @Test
    void crear_sinPresentacionDeFactor1_rechazaYNoInserta() {
        var request = request(nueva("Caja", 100, "12.90"), nueva("Blíster", 10, "1.80"));

        assertThatThrownBy(() -> service.crear(request))
                .isInstanceOf(PresentacionUnidadException.class)
                .hasMessageContaining("exactamente una presentación de factor 1");
        verify(productoDao, never()).insertar(any());
    }

    @Test
    void crear_conDosPresentacionesDeFactor1_rechazaYNoInserta() {
        var request = request(nueva("Unidad", 1, "0.20"), nueva("Tableta", 1, "0.25"));

        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(PresentacionUnidadException.class);
        verify(productoDao, never()).insertar(any());
    }

    @Test
    void crear_conEtiquetaRepetidaSinImportarMayusculas_rechaza() {
        var request = request(nueva("Unidad", 1, "0.20"), nueva("Caja", 100, "12.90"), nueva(" caja ", 50, "7.00"));

        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(PresentacionDuplicadaException.class);
        verify(productoDao, never()).insertar(any());
    }

    @Test
    void crear_conIdEnUnaPresentacion_rechaza() {
        var request = request(existente(11L, "Unidad", 1, "0.20"));

        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(PresentacionInvalidaException.class);
    }

    @Test
    void crear_conCodigoDeBarrasRepetido_devuelveCodigoBarrasDuplicado() {
        when(productoDao.insertar(any())).thenThrow(new DuplicateKeyException("uk"));

        assertThatThrownBy(() -> service.crear(request(nueva("Unidad", 1, "0.20"))))
                .isInstanceOf(CodigoBarrasDuplicadoException.class);
        verify(productoDao, never()).insertarPresentacion(any());
    }

    @Test
    void crear_valido_insertaElProductoConSuAutorYTodasSusPresentaciones() {
        when(productoDao.insertar(any())).thenReturn(PRODUCTO_ID);
        when(productoDao.listarPresentacionesPorProductos(List.of(PRODUCTO_ID))).thenReturn(Map.of());

        service.crear(request(nueva("Caja", 100, "12.90"), nueva("Blíster", 10, "1.80"), nueva("Unidad", 1, "0.20")));

        ArgumentCaptor<Producto> producto = ArgumentCaptor.forClass(Producto.class);
        verify(productoDao).insertar(producto.capture());
        assertThat(producto.getValue().getCreadoPor()).isEqualTo(USUARIO_ID);
        assertThat(producto.getValue().getBoticaId()).isEqualTo(BOTICA_ID);
        assertThat(producto.getValue().getNombre()).isEqualTo("Paracetamol 500 mg"); // sin espacios de más
        verify(productoDao, times(3)).insertarPresentacion(any());
    }

    // --- actualizar ----------------------------------------------------------

    @Test
    void actualizar_productoInexistente_devuelve404() {
        when(productoDao.obtenerPorId(BOTICA_ID, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(999L, request(nueva("Unidad", 1, "0.20"))))
                .isInstanceOf(ProductoNoEncontradoException.class);
    }

    @Test
    void actualizar_quitarUnaPresentacion_laDesactivaYNoLaBorra() {
        conPresentaciones(fila(11L, "Caja", 100, "12.90", true), fila(13L, "Unidad", 1, "0.20", true));

        service.actualizar(PRODUCTO_ID, request(existente(13L, "Unidad", 1, "0.20")));

        ArgumentCaptor<PresentacionProducto> cambios = ArgumentCaptor.forClass(PresentacionProducto.class);
        verify(productoDao, times(2)).actualizarPresentacion(cambios.capture());
        PresentacionProducto caja = cambios.getAllValues().stream().filter(p -> p.getId() == 11L).findFirst().orElseThrow();
        assertThat(caja.getActivo()).as("la caja quitada queda inactiva").isFalse();
        assertThat(caja.getPrecio()).as("y conserva su precio").isEqualByComparingTo("12.90");
    }

    @Test
    void actualizar_cambiarElPrecio_actualizaSoloEsaPresentacion() {
        conPresentaciones(fila(13L, "Unidad", 1, "0.20", true));

        service.actualizar(PRODUCTO_ID, request(existente(13L, "Unidad", 1, "0.30")));

        ArgumentCaptor<PresentacionProducto> cambio = ArgumentCaptor.forClass(PresentacionProducto.class);
        verify(productoDao).actualizarPresentacion(cambio.capture());
        assertThat(cambio.getValue().getPrecio()).isEqualByComparingTo("0.30");
        assertThat(cambio.getValue().getActivo()).isTrue();
    }

    @Test
    void actualizar_reenviarUnaPresentacionDesactivada_laReactiva() {
        conPresentaciones(fila(11L, "Caja", 100, "12.90", false), fila(13L, "Unidad", 1, "0.20", true));

        service.actualizar(PRODUCTO_ID, request(existente(11L, "Caja", 100, "13.50"), existente(13L, "Unidad", 1, "0.20")));

        ArgumentCaptor<PresentacionProducto> cambios = ArgumentCaptor.forClass(PresentacionProducto.class);
        verify(productoDao, times(2)).actualizarPresentacion(cambios.capture());
        PresentacionProducto caja = cambios.getAllValues().stream().filter(p -> p.getId() == 11L).findFirst().orElseThrow();
        assertThat(caja.getActivo()).isTrue();
        assertThat(caja.getPrecio()).isEqualByComparingTo("13.50");
    }

    @Test
    void actualizar_cambiarElFactorDeUnaExistente_rechazaSinTocarNada() {
        conPresentaciones(fila(11L, "Caja", 100, "12.90", true), fila(13L, "Unidad", 1, "0.20", true));

        assertThatThrownBy(() -> service.actualizar(PRODUCTO_ID,
                request(existente(11L, "Caja", 50, "12.90"), existente(13L, "Unidad", 1, "0.20"))))
                .isInstanceOf(FactorNoEditableException.class);
        verify(productoDao, never()).actualizar(any());
        verify(productoDao, never()).actualizarPresentacion(any());
    }

    @Test
    void actualizar_reemplazarLaUnidadPorUnaFilaNueva_rechaza() {
        // Dos filas de factor 1 (aunque una inactiva) duplicarían filas en los JOIN de merma, alertas y lotes.
        conPresentaciones(fila(13L, "Unidad", 1, "0.20", true));

        assertThatThrownBy(() -> service.actualizar(PRODUCTO_ID, request(nueva("Tableta", 1, "0.25"))))
                .isInstanceOf(PresentacionUnidadException.class)
                .hasMessageContaining("no se quita ni se reemplaza");
        verify(productoDao, never()).insertarPresentacion(any());
    }

    @Test
    void actualizar_conPresentacionDeOtroProducto_rechaza() {
        conPresentaciones(fila(13L, "Unidad", 1, "0.20", true));

        assertThatThrownBy(() -> service.actualizar(PRODUCTO_ID,
                request(existente(13L, "Unidad", 1, "0.20"), existente(999L, "Caja", 100, "9.00"))))
                .isInstanceOf(PresentacionInvalidaException.class);
    }

    @Test
    void actualizar_conCodigoDeBarrasDeOtroProducto_devuelveCodigoBarrasDuplicado() {
        conPresentaciones(fila(13L, "Unidad", 1, "0.20", true));
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk")).when(productoDao).actualizar(any());

        assertThatThrownBy(() -> service.actualizar(PRODUCTO_ID, request(existente(13L, "Unidad", 1, "0.20"))))
                .isInstanceOf(CodigoBarrasDuplicadoException.class);
    }

    // --- estado --------------------------------------------------------------

    @Test
    void cambiarEstado_productoInexistente_devuelve404() {
        when(productoDao.obtenerPorId(BOTICA_ID, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarEstado(999L, false)).isInstanceOf(ProductoNoEncontradoException.class);
        verify(productoDao, never()).cambiarEstado(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    // --- ayudas --------------------------------------------------------------

    private void conPresentaciones(PresentacionProducto... filas) {
        when(productoDao.listarPresentacionesPorProductos(List.of(PRODUCTO_ID)))
                .thenReturn(Map.of(PRODUCTO_ID, new ArrayList<>(List.of(filas))));
    }

    private static Producto producto() {
        return Producto.builder().id(PRODUCTO_ID).boticaId(BOTICA_ID).nombre("Paracetamol 500 mg")
                .categoria("Analgésicos").codigoBarras("775000").unidadNombre("tableta").build();
    }

    private static PresentacionProducto fila(Long id, String etiqueta, int factor, String precio, boolean activo) {
        return PresentacionProducto.builder().id(id).boticaId(BOTICA_ID).productoId(PRODUCTO_ID)
                .etiqueta(etiqueta).factorConversion(factor).precio(new BigDecimal(precio)).activo(activo).build();
    }

    private static PresentacionRequest nueva(String etiqueta, int factor, String precio) {
        return new PresentacionRequest(null, etiqueta, factor, new BigDecimal(precio));
    }

    private static PresentacionRequest existente(Long id, String etiqueta, int factor, String precio) {
        return new PresentacionRequest(id, etiqueta, factor, new BigDecimal(precio));
    }

    private static ProductoRequest request(PresentacionRequest... presentaciones) {
        return new ProductoRequest("  Paracetamol 500 mg ", "Genfar", "Analgésicos", "775000", "tableta", List.of(presentaciones));
    }
}
