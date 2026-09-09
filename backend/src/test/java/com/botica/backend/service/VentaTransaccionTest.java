package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dao.LoteDao;
import com.botica.backend.dao.ProductoDao;
import com.botica.backend.dao.VentaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.exception.PresentacionInvalidaException;
import com.botica.backend.exception.SinCajaAbiertaException;
import com.botica.backend.exception.StockInsuficienteException;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.model.Lote;
import com.botica.backend.model.PresentacionProducto;
import com.botica.backend.model.Producto;
import com.botica.backend.model.Venta;
import com.botica.backend.model.VentaDetalle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El cuerpo transaccional real de registrar una venta -- ver VentaServiceTest para el envoltorio de reintento de idempotencia. */
@ExtendWith(MockitoExtension.class)
class VentaTransaccionTest {

    private static final Long BOTICA_ID = 1L;
    private static final Long USUARIO_ID = 1L;
    private static final Long PRODUCTO_ID = 1L;
    private static final Long PRESENTACION_ID = 1L;

    @Mock
    private VentaDao ventaDao;
    @Mock
    private LoteDao loteDao;
    @Mock
    private ProductoDao productoDao;
    @Mock
    private CajaDao cajaDao;
    @Mock
    private ContextoOperacion contexto;

    private ConfigNegocioProperties config;
    private VentaTransaccion transaccion;

    @BeforeEach
    void configurar() {
        config = new ConfigNegocioProperties(); // igv = 0.18 por defecto
        transaccion = new VentaTransaccion(ventaDao, loteDao, productoDao, cajaDao, contexto, config);
    }

    private NuevaVentaRequest pedido(int cantidad) {
        return new NuevaVentaRequest(UUID.randomUUID(),
                List.of(new ItemVentaRequest(PRODUCTO_ID, PRESENTACION_ID, cantidad, "BUSQUEDA")), "efectivo");
    }

    private Producto productoDePrueba() {
        return Producto.builder().id(PRODUCTO_ID).boticaId(BOTICA_ID).nombre("Paracetamol 500 mg").build();
    }

    private PresentacionProducto presentacionDePrueba(int factorConversion, String precio) {
        return PresentacionProducto.builder().id(PRESENTACION_ID).productoId(PRODUCTO_ID)
                .etiqueta("Unidad").factorConversion(factorConversion).precio(new BigDecimal(precio)).build();
    }

    private Lote lote(long id, int stock, String costo, LocalDate vencimiento) {
        return Lote.builder().id(id).productoId(PRODUCTO_ID).stock(stock)
                .costoUnitario(new BigDecimal(costo)).fechaVencimiento(vencimiento).build();
    }

    @Test
    void ejecutar_conClaveYaExistente_devuelveLaVentaExistente_sinValidarNadaMas() {
        UUID clave = UUID.randomUUID();
        NuevaVentaRequest request = new NuevaVentaRequest(clave,
                List.of(new ItemVentaRequest(PRODUCTO_ID, PRESENTACION_ID, 1, "BUSQUEDA")), "efectivo");
        Venta existente = Venta.builder().id(99L).boticaId(BOTICA_ID).usuarioId(USUARIO_ID)
                .subtotal(new BigDecimal("1.00")).igv(new BigDecimal("0.18")).total(new BigDecimal("1.18"))
                .metodoPago("efectivo").claveIdempotencia(clave).sincronizada(true).build();
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(ventaDao.buscarPorClaveIdempotencia(BOTICA_ID, clave)).thenReturn(Optional.of(existente));
        when(ventaDao.listarDetallePorVenta(BOTICA_ID, 99L)).thenReturn(List.of());

        VentaResponse respuesta = transaccion.ejecutar(request);

        assertThat(respuesta.id()).isEqualTo(99L);
        verify(cajaDao, never()).buscarAbiertaDelUsuario(any(), any());
        verify(ventaDao, never()).insertarCabecera(any());
    }

    @Test
    void ejecutar_sinCajaAbierta_lanzaSinCajaAbiertaException() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(ventaDao.buscarPorClaveIdempotencia(any(), any())).thenReturn(Optional.empty());
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transaccion.ejecutar(pedido(1))).isInstanceOf(SinCajaAbiertaException.class);
        verify(ventaDao, never()).insertarCabecera(any());
    }

    @Test
    void ejecutar_conPresentacionQueNoExiste_lanzaPresentacionInvalida() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(ventaDao.buscarPorClaveIdempotencia(any(), any())).thenReturn(Optional.empty());
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID))
                .thenReturn(Optional.of(CajaDiaria.builder().id(1L).boticaId(BOTICA_ID).build()));
        when(productoDao.obtenerPorId(BOTICA_ID, PRODUCTO_ID)).thenReturn(Optional.of(productoDePrueba()));
        when(productoDao.obtenerPresentacion(BOTICA_ID, PRODUCTO_ID, PRESENTACION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transaccion.ejecutar(pedido(1))).isInstanceOf(PresentacionInvalidaException.class);
    }

    @Test
    void ejecutar_conStockInsuficiente_lanzaStockInsuficienteException_yNoInsertaNada() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(ventaDao.buscarPorClaveIdempotencia(any(), any())).thenReturn(Optional.empty());
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID))
                .thenReturn(Optional.of(CajaDiaria.builder().id(1L).boticaId(BOTICA_ID).build()));
        when(productoDao.obtenerPorId(BOTICA_ID, PRODUCTO_ID)).thenReturn(Optional.of(productoDePrueba()));
        when(productoDao.obtenerPresentacion(BOTICA_ID, PRODUCTO_ID, PRESENTACION_ID))
                .thenReturn(Optional.of(presentacionDePrueba(1, "0.50")));
        when(loteDao.bloquearLotesFefo(BOTICA_ID, PRODUCTO_ID))
                .thenReturn(List.of(lote(10L, 3, "0.30", LocalDate.of(2027, 1, 1))));

        assertThatThrownBy(() -> transaccion.ejecutar(pedido(10))).isInstanceOf(StockInsuficienteException.class);
        verify(loteDao, never()).descontarStock(any(), anyInt());
        verify(ventaDao, never()).insertarCabecera(any());
    }

    @Test
    void ejecutar_exito_extraeElIgvDelTotalEnVezDeSumarlo() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(ventaDao.buscarPorClaveIdempotencia(any(), any())).thenReturn(Optional.empty());
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID))
                .thenReturn(Optional.of(CajaDiaria.builder().id(7L).boticaId(BOTICA_ID).build()));
        when(productoDao.obtenerPorId(BOTICA_ID, PRODUCTO_ID)).thenReturn(Optional.of(productoDePrueba()));
        when(productoDao.obtenerPresentacion(BOTICA_ID, PRODUCTO_ID, PRESENTACION_ID))
                .thenReturn(Optional.of(presentacionDePrueba(1, "1.00")));
        when(loteDao.bloquearLotesFefo(BOTICA_ID, PRODUCTO_ID))
                .thenReturn(List.of(lote(10L, 100, "0.50", LocalDate.of(2027, 1, 1))));
        when(ventaDao.insertarCabecera(any())).thenAnswer(inv -> {
            Venta v = inv.getArgument(0);
            v.setId(50L);
            return v;
        });
        when(ventaDao.insertarDetalle(any())).thenAnswer(inv -> inv.getArgument(0));

        VentaResponse respuesta = transaccion.ejecutar(pedido(10)); // 10 x 1.00 = 10.00

        assertThat(respuesta.total()).isEqualByComparingTo("10.00");
        assertThat(respuesta.subtotal()).isEqualByComparingTo("8.47"); // 10 / 1.18
        assertThat(respuesta.igv()).isEqualByComparingTo("1.53");
        assertThat(respuesta.subtotal().add(respuesta.igv())).isEqualByComparingTo(respuesta.total());
    }

    @Test
    void ejecutar_repartidoEntreDosLotes_cadaDetalleCongelaElCostoDeSuPropioLote_ySumanElTotalExacto() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(ventaDao.buscarPorClaveIdempotencia(any(), any())).thenReturn(Optional.empty());
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID))
                .thenReturn(Optional.of(CajaDiaria.builder().id(7L).boticaId(BOTICA_ID).build()));
        when(productoDao.obtenerPorId(BOTICA_ID, PRODUCTO_ID)).thenReturn(Optional.of(productoDePrueba()));
        when(productoDao.obtenerPresentacion(BOTICA_ID, PRODUCTO_ID, PRESENTACION_ID))
                .thenReturn(Optional.of(presentacionDePrueba(1, "0.50")));
        // FEFO: lote 1 (vence antes, solo 6 de stock) se agota primero, lote 2 cubre el resto.
        when(loteDao.bloquearLotesFefo(BOTICA_ID, PRODUCTO_ID)).thenReturn(List.of(
                lote(1L, 6, "0.30", LocalDate.of(2026, 12, 1)),
                lote(2L, 100, "0.40", LocalDate.of(2027, 6, 1))));
        when(ventaDao.insertarCabecera(any())).thenAnswer(inv -> {
            Venta v = inv.getArgument(0);
            v.setId(60L);
            return v;
        });
        ArgumentCaptor<VentaDetalle> captor = ArgumentCaptor.forClass(VentaDetalle.class);
        when(ventaDao.insertarDetalle(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        VentaResponse respuesta = transaccion.ejecutar(pedido(10)); // 10 x 0.50 = 5.00, pide 10 unidades base

        List<VentaDetalle> detalles = captor.getAllValues();
        assertThat(detalles).hasSize(2);
        assertThat(detalles.get(0).getLoteId()).isEqualTo(1L);
        assertThat(detalles.get(0).getCantidad()).isEqualTo(6);
        assertThat(detalles.get(0).getCostoUnitario()).isEqualByComparingTo("0.30");
        assertThat(detalles.get(1).getLoteId()).isEqualTo(2L);
        assertThat(detalles.get(1).getCantidad()).isEqualTo(4);
        assertThat(detalles.get(1).getCostoUnitario()).isEqualByComparingTo("0.40");

        BigDecimal sumaLineas = detalles.get(0).getTotalLinea().add(detalles.get(1).getTotalLinea());
        assertThat(sumaLineas).isEqualByComparingTo(respuesta.total());
        verify(loteDao).descontarStock(1L, 6);
        verify(loteDao).descontarStock(2L, 4);
    }
}
