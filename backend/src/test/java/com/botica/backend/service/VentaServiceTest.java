package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dao.VentaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.exception.SinPermisoException;
import com.botica.backend.exception.VentaNoEncontradaException;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.model.Venta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El envoltorio de reintento de idempotencia bajo concurrencia -- ver
 * VentaTransaccionTest para las reglas de negocio (FEFO, IGV, etc.).
 * Esto prueba específicamente lo que pidió el usuario: dos peticiones
 * con la misma claveIdempotencia que chocan en el INSERT no deben dar
 * un 500, deben devolver la venta que ganó la carrera.
 */
@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    private static final Long BOTICA_ID = 1L;

    @Mock
    private VentaTransaccion transaccion;
    @Mock
    private VentaDao ventaDao;
    @Mock
    private CajaDao cajaDao;
    @Mock
    private ContextoOperacion contexto;

    // --- D-E2-2: quién ve el historial de ventas ---

    private static final Long USUARIO_TECNICO = 7L;
    private static final Long CAJA_DEL_TECNICO = 70L;
    private static final Long OTRA_CAJA = 71L;

    @Test
    void listar_comoTecnico_soloPideLasVentasDeSuCajaAbierta() {
        comoTecnicoConCaja(CAJA_DEL_TECNICO);
        when(ventaDao.listarPaginado(BOTICA_ID, CAJA_DEL_TECNICO, 0, 20, "fecha,desc"))
                .thenReturn(PaginaResponse.de(List.of(), 0, 20, 0L));

        new VentaService(transaccion, ventaDao, cajaDao, contexto).listar(0, 20, "fecha,desc");

        verify(ventaDao).listarPaginado(BOTICA_ID, CAJA_DEL_TECNICO, 0, 20, "fecha,desc");
    }

    @Test
    void listar_comoTecnicoSinCajaAbierta_devuelvePaginaVacia_sinConsultarVentas() {
        when(contexto.rol()).thenReturn("TECNICO");
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_TECNICO);
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_TECNICO)).thenReturn(Optional.empty());

        PaginaResponse<VentaResponse> pagina = new VentaService(transaccion, ventaDao, cajaDao, contexto).listar(0, 20, "fecha,desc");

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
        verify(ventaDao, never()).listarPaginado(any(), any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void listar_comoAdministrador_pideTodaLaBotica_sinFiltroDeCaja() {
        when(contexto.rol()).thenReturn("ADMINISTRADOR");
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(ventaDao.listarPaginado(BOTICA_ID, null, 0, 20, "fecha,desc"))
                .thenReturn(PaginaResponse.de(List.of(), 0, 20, 0L));

        new VentaService(transaccion, ventaDao, cajaDao, contexto).listar(0, 20, "fecha,desc");

        verify(ventaDao).listarPaginado(BOTICA_ID, null, 0, 20, "fecha,desc");
        verify(cajaDao, never()).buscarAbiertaDelUsuario(anyLong(), anyLong());
    }

    @Test
    void obtenerPorId_comoTecnico_ventaDeOtraCajaDeSuBotica_da403() {
        comoTecnicoConCaja(CAJA_DEL_TECNICO);
        when(ventaDao.buscarPorId(BOTICA_ID, 5L)).thenReturn(Optional.of(ventaDeCaja(5L, OTRA_CAJA)));

        assertThatThrownBy(() -> new VentaService(transaccion, ventaDao, cajaDao, contexto).obtenerPorId(5L))
                .isInstanceOf(SinPermisoException.class);
    }

    @Test
    void obtenerPorId_comoTecnicoSinCajaAbierta_da403() {
        when(contexto.rol()).thenReturn("TECNICO");
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_TECNICO);
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_TECNICO)).thenReturn(Optional.empty());
        when(ventaDao.buscarPorId(BOTICA_ID, 5L)).thenReturn(Optional.of(ventaDeCaja(5L, OTRA_CAJA)));

        assertThatThrownBy(() -> new VentaService(transaccion, ventaDao, cajaDao, contexto).obtenerPorId(5L))
                .isInstanceOf(SinPermisoException.class);
    }

    @Test
    void obtenerPorId_comoTecnico_ventaDeSuCaja_laDevuelve() {
        comoTecnicoConCaja(CAJA_DEL_TECNICO);
        Venta propia = ventaDeCaja(5L, CAJA_DEL_TECNICO);
        when(ventaDao.buscarPorId(BOTICA_ID, 5L)).thenReturn(Optional.of(propia));
        when(transaccion.construirRespuestaDesdeVentaExistente(propia)).thenReturn(respuestaDePrueba(5L, UUID.randomUUID()));

        assertThat(new VentaService(transaccion, ventaDao, cajaDao, contexto).obtenerPorId(5L).id()).isEqualTo(5L);
    }

    @Test
    void obtenerPorId_comoAdministrador_ventaDeCualquierCajaDeSuBotica_laDevuelve() {
        when(contexto.rol()).thenReturn("ADMINISTRADOR");
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        Venta ajena = ventaDeCaja(5L, OTRA_CAJA);
        when(ventaDao.buscarPorId(BOTICA_ID, 5L)).thenReturn(Optional.of(ajena));
        when(transaccion.construirRespuestaDesdeVentaExistente(ajena)).thenReturn(respuestaDePrueba(5L, UUID.randomUUID()));

        assertThat(new VentaService(transaccion, ventaDao, cajaDao, contexto).obtenerPorId(5L).id()).isEqualTo(5L);
    }

    @Test
    void obtenerPorId_ventaDeOtraBotica_da404_noRevelaQueExiste() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(ventaDao.buscarPorId(BOTICA_ID, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new VentaService(transaccion, ventaDao, cajaDao, contexto).obtenerPorId(999L))
                .isInstanceOf(VentaNoEncontradaException.class);
    }

    private void comoTecnicoConCaja(Long cajaId) {
        when(contexto.rol()).thenReturn("TECNICO");
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_TECNICO);
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_TECNICO))
                .thenReturn(Optional.of(CajaDiaria.builder().id(cajaId).boticaId(BOTICA_ID).usuarioId(USUARIO_TECNICO).build()));
    }

    private static Venta ventaDeCaja(Long ventaId, Long cajaId) {
        return Venta.builder().id(ventaId).boticaId(BOTICA_ID).cajaId(cajaId).build();
    }

    @Test
    void registrar_sinConflicto_delegaDirectoATransaccion() {
        NuevaVentaRequest request = pedido();
        VentaResponse esperado = respuestaDePrueba(1L, request.claveIdempotencia());
        when(transaccion.ejecutar(request)).thenReturn(esperado);

        VentaService service = new VentaService(transaccion, ventaDao, cajaDao, contexto);
        VentaResponse resultado = service.registrar(request);

        assertThat(resultado).isEqualTo(esperado);
    }

    @Test
    void registrar_conViolacionDeUnicidadPorCarreraConcurrente_devuelveLaVentaGanadora_noUn500() {
        NuevaVentaRequest request = pedido();
        when(transaccion.ejecutar(request)).thenThrow(new DuplicateKeyException("llave duplicada"));
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        Venta ventaGanadora = Venta.builder().id(42L).boticaId(BOTICA_ID).claveIdempotencia(request.claveIdempotencia()).build();
        when(ventaDao.buscarPorClaveIdempotencia(BOTICA_ID, request.claveIdempotencia())).thenReturn(Optional.of(ventaGanadora));
        VentaResponse respuestaReconstruida = respuestaDePrueba(42L, request.claveIdempotencia());
        when(transaccion.construirRespuestaDesdeVentaExistente(ventaGanadora)).thenReturn(respuestaReconstruida);

        VentaService service = new VentaService(transaccion, ventaDao, cajaDao, contexto);
        VentaResponse resultado = service.registrar(request);

        assertThat(resultado.id()).isEqualTo(42L);
    }

    @Test
    void registrar_conViolacionDeUnicidad_siNoEncuentraLaVentaGanadora_relanzaLaExcepcionOriginal() {
        // Caso extremo (no debería pasar nunca en la práctica): si la violación de
        // unicidad ocurrió pero la búsqueda posterior no encuentra nada, mejor un
        // error visible que devolver null silenciosamente.
        NuevaVentaRequest request = pedido();
        DuplicateKeyException original = new DuplicateKeyException("llave duplicada");
        when(transaccion.ejecutar(request)).thenThrow(original);
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(ventaDao.buscarPorClaveIdempotencia(BOTICA_ID, request.claveIdempotencia())).thenReturn(Optional.empty());

        VentaService service = new VentaService(transaccion, ventaDao, cajaDao, contexto);

        assertThatThrownBy(() -> service.registrar(request)).isSameAs(original);
    }

    private NuevaVentaRequest pedido() {
        return new NuevaVentaRequest(UUID.randomUUID(),
                List.of(new ItemVentaRequest(1L, 1L, 1, "BUSQUEDA")), "efectivo", null);
    }

    private VentaResponse respuestaDePrueba(Long id, UUID clave) {
        return new VentaResponse(id, OffsetDateTime.now(), 1L, List.of(),
                new BigDecimal("1.00"), new BigDecimal("0.18"), new BigDecimal("1.18"), "efectivo", true, clave, null);
    }
}
