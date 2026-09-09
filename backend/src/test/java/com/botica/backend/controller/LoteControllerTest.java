package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.config.SecurityConfig;
import com.botica.backend.dto.LoteResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.exception.ProductoNoEncontradoException;
import com.botica.backend.service.LoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoteController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class LoteControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private LoteService loteService;

    @Test
    void listar_devuelvePaginaResponseDelService() throws Exception {
        LoteResponse lote = loteDePrueba();
        when(loteService.listar(any(), any(), eq(false), any(), anyInt(), anyInt(), any()))
                .thenReturn(PaginaResponse.de(List.of(lote), 0, 20, 1));

        mockMvc.perform(get("/api/lotes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].codigo").value("L-1"))
                .andExpect(jsonPath("$.contenido[0].costoUnitario").doesNotExist())
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void registrar_conProductoInexistente_devuelve404ProductoNoEncontrado() throws Exception {
        when(loteService.registrar(any())).thenThrow(new ProductoNoEncontradoException());

        mockMvc.perform(post("/api/lotes")
                        .contentType("application/json")
                        .content("""
                                {"productoId": 999, "codigo": "L-1", "fechaVencimiento": "2027-01-01", "stock": 10, "costoUnitario": 3.00}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCTO_NO_ENCONTRADO"));
    }

    @Test
    void registrar_sinCostoUnitario_devuelve400FormatoInvalido() throws Exception {
        mockMvc.perform(post("/api/lotes")
                        .contentType("application/json")
                        .content("""
                                {"productoId": 1, "codigo": "L-1", "fechaVencimiento": "2027-01-01", "stock": 10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));
    }

    private LoteResponse loteDePrueba() {
        return new LoteResponse(1L, 1L, "Paracetamol 500 mg", "Analgésicos", "L-1",
                LocalDate.of(2027, 1, 1), 10, "A-1", new BigDecimal("0.20"), "OK", "OK");
    }
}
