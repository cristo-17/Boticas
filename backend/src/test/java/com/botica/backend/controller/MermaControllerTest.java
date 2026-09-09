package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.config.SecurityConfig;
import com.botica.backend.dto.MermaResponse;
import com.botica.backend.exception.LoteNoEncontradoException;
import com.botica.backend.exception.ObservacionRequeridaException;
import com.botica.backend.exception.SinCajaAbiertaException;
import com.botica.backend.exception.StockInsuficienteMermaException;
import com.botica.backend.service.MermaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.security.test.context.support.WithMockUser;

@WebMvcTest(MermaController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@WithMockUser(roles = "ADMINISTRADOR")
class MermaControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private MermaService mermaService;

    private static final MermaResponse MERMA_MOCK = new MermaResponse(
            1L, 10L, "Paracetamol 500 mg", "L-001", 3,
            "Vencimiento", null, new BigDecimal("0.60"), 1L, OffsetDateTime.now()
    );

    private static final String CUERPO_VALIDO = """
            {
              "loteId": 10,
              "cantidad": 3,
              "motivo": "Vencimiento"
            }
            """;

    @Test
    void listar_devuelve200ConLista() throws Exception {
        when(mermaService.listarDelDia()).thenReturn(List.of(MERMA_MOCK));

        mockMvc.perform(get("/api/mermas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productoNombre").value("Paracetamol 500 mg"))
                .andExpect(jsonPath("$[0].valorVenta").value(0.60));
    }

    @Test
    void registrar_exito_devuelve200() throws Exception {
        when(mermaService.registrar(any())).thenReturn(MERMA_MOCK);

        mockMvc.perform(post("/api/mermas").contentType("application/json").content(CUERPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.motivo").value("Vencimiento"));
    }

    @Test
    void registrar_sinCaja_devuelve409() throws Exception {
        when(mermaService.registrar(any())).thenThrow(new SinCajaAbiertaException());

        mockMvc.perform(post("/api/mermas").contentType("application/json").content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SIN_CAJA_ABIERTA"));
    }

    @Test
    void registrar_loteNoExiste_devuelve404() throws Exception {
        when(mermaService.registrar(any())).thenThrow(new LoteNoEncontradoException());

        mockMvc.perform(post("/api/mermas").contentType("application/json").content(CUERPO_VALIDO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("LOTE_NO_ENCONTRADO"));
    }

    @Test
    void registrar_stockInsuficiente_devuelve422() throws Exception {
        when(mermaService.registrar(any())).thenThrow(new StockInsuficienteMermaException(1));

        mockMvc.perform(post("/api/mermas").contentType("application/json").content(CUERPO_VALIDO))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("STOCK_INSUFICIENTE_MERMA"));
    }

    @Test
    void registrar_observacionRequerida_devuelve422() throws Exception {
        when(mermaService.registrar(any())).thenThrow(new ObservacionRequeridaException());

        mockMvc.perform(post("/api/mermas").contentType("application/json").content(CUERPO_VALIDO))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("OBSERVACION_REQUERIDA"));
    }

    @Test
    void registrar_cuerpoInvalido_sinLoteId_devuelve400() throws Exception {
        String cuerpoSinLoteId = """
                { "cantidad": 3, "motivo": "Vencimiento" }
                """;

        mockMvc.perform(post("/api/mermas").contentType("application/json").content(cuerpoSinLoteId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));
    }

    @Test
    void registrar_motivoInvalido_devuelve400() throws Exception {
        String cuerpoMotivoInvalido = """
                { "loteId": 10, "cantidad": 3, "motivo": "InventadoPorMi" }
                """;

        mockMvc.perform(post("/api/mermas").contentType("application/json").content(cuerpoMotivoInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));
    }
}
