package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.ProductoDao;
import com.botica.backend.dto.ProductoResponse;
import com.botica.backend.exception.ProductoNoEncontradoException;
import com.botica.backend.model.PresentacionProducto;
import com.botica.backend.model.Producto;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    private static final Long BOTICA_ID = 1L;
    private static final LocalDate HOY = LocalDate.of(2026, 9, 8);

    @Mock
    private ProductoDao productoDao;
    @Mock
    private ContextoOperacion contexto;

    private ConfigNegocioProperties config;
    private ProductoService service;

    @BeforeEach
    void configurar() {
        config = new ConfigNegocioProperties();
        Clock relojFijo = Clock.fixed(
                HOY.atTime(12, 0).atOffset(ZoneOffset.of("-05:00")).toInstant(),
                FechaNegocio.ZONA_LIMA);
        service = new ProductoService(productoDao, contexto, new FechaNegocio(relojFijo), config);
    }

    private Producto productoDePrueba() {
        return Producto.builder()
                .id(1L).boticaId(BOTICA_ID).nombre("Paracetamol 500 mg")
                .laboratorio("Genfar").categoria("Analgésicos")
                .codigoBarras("777").unidadNombre("tableta")
                .build();
    }

    @Test
    void buscar_conLoteCriticoConStock_calculaEstadoCritico() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(productoDao.buscar(BOTICA_ID, "para", 20)).thenReturn(List.of(productoDePrueba()));
        when(productoDao.fechaVencimientoMasProximaPorProductos(List.of(1L)))
                .thenReturn(Map.of(1L, HOY.plusDays(10))); // dentro de los 30 días críticos
        when(productoDao.listarPresentacionesPorProductos(List.of(1L))).thenReturn(Map.of());

        List<ProductoResponse> resultado = service.buscar("para");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).estadoVencimiento()).isEqualTo("CRITICO");
        assertThat(resultado.get(0).fechaVencimiento()).isEqualTo(HOY.plusDays(10));
    }

    @Test
    void buscar_sinNingunLoteConStock_estadoYFechaQuedanNull() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(productoDao.buscar(BOTICA_ID, "", 20)).thenReturn(List.of(productoDePrueba()));
        when(productoDao.fechaVencimientoMasProximaPorProductos(List.of(1L))).thenReturn(Map.of());
        when(productoDao.listarPresentacionesPorProductos(List.of(1L))).thenReturn(Map.of());

        List<ProductoResponse> resultado = service.buscar("");

        assertThat(resultado.get(0).estadoVencimiento()).isNull();
        assertThat(resultado.get(0).fechaVencimiento()).isNull();
    }

    @Test
    void buscar_incluyePresentacionesConUnidadNombreDelProducto_sinDetalleCompuesto() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        Producto producto = productoDePrueba();
        PresentacionProducto presentacion = PresentacionProducto.builder()
                .id(1L).productoId(1L).etiqueta("Caja").factorConversion(100)
                .precio(new java.math.BigDecimal("12.90")).build();
        when(productoDao.buscar(BOTICA_ID, "", 20)).thenReturn(List.of(producto));
        when(productoDao.fechaVencimientoMasProximaPorProductos(List.of(1L))).thenReturn(Map.of());
        when(productoDao.listarPresentacionesPorProductos(List.of(1L))).thenReturn(Map.of(1L, List.of(presentacion)));

        List<ProductoResponse> resultado = service.buscar("");

        assertThat(resultado.get(0).presentaciones()).hasSize(1);
        assertThat(resultado.get(0).presentaciones().get(0).unidadNombre()).isEqualTo("tableta");
        assertThat(resultado.get(0).presentaciones().get(0).factorConversion()).isEqualTo(100);
    }

    @Test
    void buscarPorCodigoBarras_sinCoincidencia_lanzaProductoNoEncontrado() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(productoDao.buscarPorCodigoBarras(BOTICA_ID, "000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorCodigoBarras("000"))
                .isInstanceOf(ProductoNoEncontradoException.class);
    }

    @Test
    void masVendidos_pideAlDaoElLimiteDeSeis() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(productoDao.masVendidos(BOTICA_ID, 6)).thenReturn(List.of(productoDePrueba()));
        when(productoDao.fechaVencimientoMasProximaPorProductos(List.of(1L))).thenReturn(Map.of());
        when(productoDao.listarPresentacionesPorProductos(List.of(1L))).thenReturn(Map.of());

        List<ProductoResponse> resultado = service.masVendidos();

        assertThat(resultado).hasSize(1);
    }
}
