package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.VentaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.VentaResponse;
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
    private ContextoOperacion contexto;

    @Test
    void registrar_sinConflicto_delegaDirectoATransaccion() {
        NuevaVentaRequest request = pedido();
        VentaResponse esperado = respuestaDePrueba(1L, request.claveIdempotencia());
        when(transaccion.ejecutar(request)).thenReturn(esperado);

        VentaService service = new VentaService(transaccion, ventaDao, contexto);
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

        VentaService service = new VentaService(transaccion, ventaDao, contexto);
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

        VentaService service = new VentaService(transaccion, ventaDao, contexto);

        assertThatThrownBy(() -> service.registrar(request)).isSameAs(original);
    }

    private NuevaVentaRequest pedido() {
        return new NuevaVentaRequest(UUID.randomUUID(),
                List.of(new ItemVentaRequest(1L, 1L, 1, "BUSQUEDA")), "efectivo");
    }

    private VentaResponse respuestaDePrueba(Long id, UUID clave) {
        return new VentaResponse(id, OffsetDateTime.now(), 1L, List.of(),
                new BigDecimal("1.00"), new BigDecimal("0.18"), new BigDecimal("1.18"), "efectivo", true, clave);
    }
}
