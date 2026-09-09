package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.config.SecurityConfig;
import com.botica.backend.dto.ProductoResponse;
import com.botica.backend.exception.ProductoNoEncontradoException;
import com.botica.backend.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductoController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProductoControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ProductoService productoService;

    @Test
    void buscar_devuelveLaListaDelService() throws Exception {
        when(productoService.buscar(anyString())).thenReturn(List.of(productoDePrueba()));

        mockMvc.perform(get("/api/productos?buscar=para"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Paracetamol 500 mg"))
                .andExpect(jsonPath("$[0].presentaciones[0].unidadNombre").value("tableta"));
    }

    @Test
    void buscarPorCodigoBarras_sinCoincidencia_devuelve404ProductoNoEncontrado() throws Exception {
        when(productoService.buscarPorCodigoBarras("000")).thenThrow(new ProductoNoEncontradoException());

        mockMvc.perform(get("/api/productos/codigo/000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCTO_NO_ENCONTRADO"));
    }

    @Test
    void masVendidos_devuelveLaListaDelService() throws Exception {
        when(productoService.masVendidos()).thenReturn(List.of(productoDePrueba()));

        mockMvc.perform(get("/api/productos/mas-vendidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }

    private ProductoResponse productoDePrueba() {
        return new ProductoResponse(
                1L, "Paracetamol 500 mg", "Genfar", "Analgésicos", "777",
                null, null,
                List.of(new com.botica.backend.dto.PresentacionResponse(1L, "Caja", 100, "tableta", new java.math.BigDecimal("12.90")))
        );
    }
}
