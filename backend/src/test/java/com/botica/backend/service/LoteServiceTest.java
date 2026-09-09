package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.LoteDao;
import com.botica.backend.dto.LoteResponse;
import com.botica.backend.dto.NuevoLoteRequest;
import com.botica.backend.exception.ProductoNoEncontradoException;
import com.botica.backend.model.Lote;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoteServiceTest {

    private static final Long BOTICA_ID = 1L;
    private static final Long USUARIO_ID = 1L;
    private static final LocalDate HOY = LocalDate.of(2026, 9, 8);

    @Mock
    private LoteDao loteDao;
    @Mock
    private ContextoOperacion contexto;

    private ConfigNegocioProperties config;
    private LoteService service;

    @BeforeEach
    void configurar() {
        config = new ConfigNegocioProperties();
        Clock relojFijo = Clock.fixed(
                HOY.atTime(12, 0).atOffset(ZoneOffset.of("-05:00")).toInstant(),
                FechaNegocio.ZONA_LIMA);
        service = new LoteService(loteDao, contexto, new FechaNegocio(relojFijo), config);
    }

    @Test
    void registrar_conProductoInexistente_lanzaProductoNoEncontrado_yNoInserta() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(loteDao.existeProducto(BOTICA_ID, 999L)).thenReturn(false);
        NuevoLoteRequest request = new NuevoLoteRequest(999L, "L-1", LocalDate.of(2027, 1, 1), 10, "A-1", new BigDecimal("3.00"));

        assertThatThrownBy(() -> service.registrar(request)).isInstanceOf(ProductoNoEncontradoException.class);
        verify(loteDao, never()).insertar(any());
    }

    @Test
    void registrar_conProductoExistente_insertaYReleeComoLoteResponse() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(loteDao.existeProducto(BOTICA_ID, 1L)).thenReturn(true);
        Lote insertado = Lote.builder().id(10L).boticaId(BOTICA_ID).productoId(1L).build();
        when(loteDao.insertar(any())).thenReturn(insertado);
        LoteResponse esperado = new LoteResponse(10L, 1L, "Paracetamol 500 mg", "Analgésicos", "L-1",
                LocalDate.of(2027, 1, 1), 10, "A-1", new BigDecimal("0.20"), "OK", "OK");
        when(loteDao.buscarResponsePorId(eq(BOTICA_ID), eq(10L), eq(HOY), eq(30), eq(90), eq(15)))
                .thenReturn(Optional.of(esperado));

        NuevoLoteRequest request = new NuevoLoteRequest(1L, "L-1", LocalDate.of(2027, 1, 1), 10, "A-1", new BigDecimal("3.00"));
        LoteResponse resultado = service.registrar(request);

        assertThat(resultado).isEqualTo(esperado);
        assertThat(resultado.toString()).doesNotContain("costoUnitario");
    }

    @Test
    void listar_delegaAlDaoConLosParametrosDeConfigYFecha() {
        when(contexto.boticaId()).thenReturn(BOTICA_ID);

        service.listar("Todas", "todos", false, null, 0, 20, null);

        verify(loteDao).listarPaginado(
                eq(BOTICA_ID), eq("Todas"), eq("todos"), eq(false), eq((Long) null),
                eq(0), eq(20), eq((String) null), eq(HOY), eq(30), eq(90), eq(15));
    }
}
