package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.dto.MermaResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.exception.CantidadExcedeStockException;
import com.botica.backend.exception.LoteNoEncontradoException;
import com.botica.backend.exception.ObservacionRequeridaException;
import com.botica.backend.service.MermaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

@WebMvcTest(MermaController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class MermaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private com.botica.backend.util.JwtUtil jwtUtil;

    @MockitoBean
    private MermaService mermaService;

    @Test
    void listar_devuelvePaginaResponseDelService() throws Exception {
        when(mermaService.listarDelDia(0, 20, null))
                .thenReturn(PaginaResponse.de(List.of(mermaDePrueba()), 0, 20, 1));

        mockMvc.perform(get("/api/mermas?fecha=hoy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].valorVenta").value(34.50))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void registrar_conCantidadQueExcedeStock_devuelve422CantidadExcedeStock() throws Exception {
        when(mermaService.registrar(any())).thenThrow(new CantidadExcedeStockException(5, 10));

        mockMvc.perform(post("/api/mermas")
                        .contentType("application/json")
                        .content("""
                                {"loteId": 6, "cantidad": 10, "motivo": "Vencimiento"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("CANTIDAD_EXCEDE_STOCK"));
    }

    @Test
    void registrar_conMotivoOtroSinObservacion_devuelve400ObservacionRequerida() throws Exception {
        when(mermaService.registrar(any())).thenThrow(new ObservacionRequeridaException());

        mockMvc.perform(post("/api/mermas")
                        .contentType("application/json")
                        .content("""
                                {"loteId": 6, "cantidad": 2, "motivo": "Otro"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("OBSERVACION_REQUERIDA"));
    }

    @Test
    void registrar_conLoteInexistente_devuelve404LoteNoEncontrado() throws Exception {
        when(mermaService.registrar(any())).thenThrow(new LoteNoEncontradoException());

        mockMvc.perform(post("/api/mermas")
                        .contentType("application/json")
                        .content("""
                                {"loteId": 999, "cantidad": 2, "motivo": "Vencimiento"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("LOTE_NO_ENCONTRADO"));
    }

    @Test
    void registrar_sinCantidad_devuelve400FormatoInvalido() throws Exception {
        mockMvc.perform(post("/api/mermas")
                        .contentType("application/json")
                        .content("""
                                {"loteId": 6, "motivo": "Vencimiento"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));
    }

    private MermaResponse mermaDePrueba() {
        return new MermaResponse(3001L, 6L, "Omeprazol 20 mg", "L-2311D", 3, "Vencimiento", null,
                new BigDecimal("34.50"), 1L, OffsetDateTime.parse("2026-09-08T11:15:00-05:00"));
    }
}
