package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.LoteDao;
import com.botica.backend.dao.MermaDao;
import com.botica.backend.dto.ConfigResponse;
import com.botica.backend.dto.MermaResponse;
import com.botica.backend.dto.NuevaMermaRequest;
import com.botica.backend.dto.VencimientoConfig;
import com.botica.backend.exception.CantidadExcedeStockException;
import com.botica.backend.exception.LoteNoEncontradoException;
import com.botica.backend.exception.MotivoInvalidoException;
import com.botica.backend.exception.ObservacionRequeridaException;
import com.botica.backend.model.Merma;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Es destructiva y sin deshacer (CLAUDE.md): estas pruebas son
 * exactamente las que el usuario pidió ver con evidencia real además
 * de acá -- cantidad topada al stock del servidor (no del cliente),
 * motivo "Otro"/"Robo o pérdida" sin observación rechazado, y el caso
 * exitoso con el descuento y el movimiento de stock correctos.
 */
@ExtendWith(MockitoExtension.class)
class MermaServiceTest {

    private static final Long BOTICA_ID = 1L;
    private static final Long USUARIO_ID = 7L;
    private static final ConfigResponse CONFIG = new ConfigResponse(
            new BigDecimal("0.18"), 15, new BigDecimal("10.00"),
            List.of("Vencimiento", "Rotura", "Deterioro", "Robo o pérdida", "Otro"),
            List.of("Robo o pérdida", "Otro"),
            new VencimientoConfig(30, 90));

    @Mock
    private MermaDao mermaDao;
    @Mock
    private LoteDao loteDao;
    @Mock
    private ContextoOperacion contexto;
    @Mock
    private ConfigService configService;

    private MermaService service;

    @BeforeEach
    void configurar() {
        service = new MermaService(mermaDao, loteDao, contexto, configService, new FechaNegocio());
    }

    @Test
    void registrar_conLoteInexistente_lanzaLoteNoEncontrado() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(mermaDao.bloquearLoteConPrecio(BOTICA_ID, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(new NuevaMermaRequest(99L, 1, "Vencimiento", null)))
                .isInstanceOf(LoteNoEncontradoException.class);
        verify(mermaDao, never()).insertar(any());
    }

    @Test
    void registrar_conCantidadMayorAlStockDelServidor_lanzaCantidadExcedeStock_yNoInsertaNiDescuenta() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(mermaDao.bloquearLoteConPrecio(BOTICA_ID, 6L))
                .thenReturn(Optional.of(new MermaDao.LoteParaMerma(6L, 2L, "Omeprazol 20 mg", "L-2311D", 5, new BigDecimal("11.50"))));
        when(configService.obtener()).thenReturn(CONFIG);

        // El cliente manda 10; el servidor, con FOR UPDATE, solo ve 5 -- la cantidad topada al stock real se valida ACÁ, no en un botón deshabilitado.
        assertThatThrownBy(() -> service.registrar(new NuevaMermaRequest(6L, 10, "Vencimiento", null)))
                .isInstanceOf(CantidadExcedeStockException.class);
        verify(mermaDao, never()).insertar(any());
        verify(loteDao, never()).descontarStock(any(), anyInt());
    }

    @Test
    void registrar_conMotivoOtroSinObservacion_lanzaObservacionRequerida() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(mermaDao.bloquearLoteConPrecio(BOTICA_ID, 6L))
                .thenReturn(Optional.of(new MermaDao.LoteParaMerma(6L, 2L, "Omeprazol 20 mg", "L-2311D", 5, new BigDecimal("11.50"))));
        when(configService.obtener()).thenReturn(CONFIG);

        assertThatThrownBy(() -> service.registrar(new NuevaMermaRequest(6L, 2, "Otro", "  ")))
                .isInstanceOf(ObservacionRequeridaException.class);
        verify(mermaDao, never()).insertar(any());
    }

    @Test
    void registrar_conMotivoQueNoEstaEnConfig_lanzaMotivoInvalido() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(mermaDao.bloquearLoteConPrecio(BOTICA_ID, 6L))
                .thenReturn(Optional.of(new MermaDao.LoteParaMerma(6L, 2L, "Omeprazol 20 mg", "L-2311D", 5, new BigDecimal("11.50"))));
        when(configService.obtener()).thenReturn(CONFIG);

        assertThatThrownBy(() -> service.registrar(new NuevaMermaRequest(6L, 2, "Inventado", null)))
                .isInstanceOf(MotivoInvalidoException.class);
    }

    @Test
    void registrar_exitosa_descuentaElLoteBloqueado_yRegistraElMovimientoDeStockConOrigenNulo() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(mermaDao.bloquearLoteConPrecio(BOTICA_ID, 6L))
                .thenReturn(Optional.of(new MermaDao.LoteParaMerma(6L, 2L, "Omeprazol 20 mg", "L-2311D", 7, new BigDecimal("11.50"))));
        when(configService.obtener()).thenReturn(CONFIG);
        when(mermaDao.insertar(any())).thenAnswer(inv -> {
            Merma m = inv.getArgument(0);
            m.setId(3001L);
            return m;
        });

        MermaResponse resultado = service.registrar(new NuevaMermaRequest(6L, 3, "Vencimiento", null));

        assertThat(resultado.id()).isEqualTo(3001L);
        assertThat(resultado.loteId()).isEqualTo(6L);
        assertThat(resultado.valorVenta()).isEqualByComparingTo(new BigDecimal("34.50")); // 3 x 11.50
        assertThat(resultado.usuarioId()).isEqualTo(USUARIO_ID);

        verify(loteDao).descontarStock(6L, 3);

        ArgumentCaptor<com.botica.backend.model.MovimientoStock> captor = ArgumentCaptor.forClass(com.botica.backend.model.MovimientoStock.class);
        verify(mermaDao).insertarMovimientoStock(captor.capture());
        com.botica.backend.model.MovimientoStock movimiento = captor.getValue();
        assertThat(movimiento.getTipo()).isEqualTo("MERMA");
        assertThat(movimiento.getCantidad()).isEqualTo(-3);
        // D4 corregido: NULL, nunca 'BUSQUEDA' -- una merma no se busca ni se escanea.
        assertThat(movimiento.getOrigenCaptura()).isNull();
        assertThat(movimiento.getReferenciaMermaId()).isEqualTo(3001L);
    }
}
