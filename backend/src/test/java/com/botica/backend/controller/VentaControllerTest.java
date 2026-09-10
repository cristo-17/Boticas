package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.dto.ItemVentaResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.exception.SinCajaAbiertaException;
import com.botica.backend.exception.StockInsuficienteException;
import com.botica.backend.exception.VentaNoEncontradaException;
import com.botica.backend.service.VentaService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VentaController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class VentaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private com.botica.backend.util.JwtUtil jwtUtil;

    @MockitoBean
    private VentaService ventaService;

    private static final String CUERPO_VALIDO = """
            {
              "claveIdempotencia": "b3f1c2a0-7e4d-4f2a-9c1e-0a1b2c3d4e5f",
              "items": [ { "productoId": 1, "presentacionId": 1, "cantidad": 2, "origenCaptura": "BUSQUEDA" } ],
              "metodoPago": "efectivo"
            }
            """;

    @Test
    void registrar_exito_devuelve200ConLaVenta() throws Exception {
        VentaResponse respuesta = new VentaResponse(1L, OffsetDateTime.now(), 1L,
                List.of(new ItemVentaResponse(1L, 1L, "Paracetamol 500 mg", "Unidad", new BigDecimal("0.20"), 2, "BUSQUEDA")),
                new BigDecimal("0.34"), new BigDecimal("0.06"), new BigDecimal("0.40"), "efectivo", true,
                UUID.fromString("b3f1c2a0-7e4d-4f2a-9c1e-0a1b2c3d4e5f"));
        when(ventaService.registrar(any())).thenReturn(respuesta);

        mockMvc.perform(post("/api/ventas").contentType("application/json").content(CUERPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0.40))
                .andExpect(jsonPath("$.items[0].origenCaptura").value("BUSQUEDA"));
    }

    @Test
    void registrar_sinCajaAbierta_devuelve409() throws Exception {
        when(ventaService.registrar(any())).thenThrow(new SinCajaAbiertaException());

        mockMvc.perform(post("/api/ventas").contentType("application/json").content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SIN_CAJA_ABIERTA"));
    }

    @Test
    void registrar_stockInsuficiente_devuelve422() throws Exception {
        when(ventaService.registrar(any())).thenThrow(new StockInsuficienteException("Paracetamol 500 mg", 3, 10));

        mockMvc.perform(post("/api/ventas").contentType("application/json").content(CUERPO_VALIDO))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("STOCK_INSUFICIENTE"));
    }

    @Test
    void registrar_conMetodoPagoInvalido_devuelve400FormatoInvalido() throws Exception {
        String cuerpo = """
                {
                  "claveIdempotencia": "b3f1c2a0-7e4d-4f2a-9c1e-0a1b2c3d4e5f",
                  "items": [ { "productoId": 1, "presentacionId": 1, "cantidad": 2, "origenCaptura": "BUSQUEDA" } ],
                  "metodoPago": "bitcoin"
                }
                """;

        mockMvc.perform(post("/api/ventas").contentType("application/json").content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));
    }

    @Test
    void registrar_conOrigenCapturaInvalido_devuelve400FormatoInvalido() throws Exception {
        String cuerpo = """
                {
                  "claveIdempotencia": "b3f1c2a0-7e4d-4f2a-9c1e-0a1b2c3d4e5f",
                  "items": [ { "productoId": 1, "presentacionId": 1, "cantidad": 2, "origenCaptura": "TELEPATIA" } ],
                  "metodoPago": "efectivo"
                }
                """;

        mockMvc.perform(post("/api/ventas").contentType("application/json").content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));
    }

    @Test
    void listar_devuelve200ConPaginaDeVentas() throws Exception {
        VentaResponse venta = new VentaResponse(1L, OffsetDateTime.now(), 1L,
                List.of(new ItemVentaResponse(1L, 1L, "Paracetamol 500 mg", "Unidad", new BigDecimal("0.20"), 2, "BUSQUEDA")),
                new BigDecimal("0.34"), new BigDecimal("0.06"), new BigDecimal("0.40"), "efectivo", true,
                UUID.randomUUID());
        PaginaResponse<VentaResponse> pagina = PaginaResponse.de(List.of(venta), 0, 20, 1L);
        when(ventaService.listar(anyInt(), anyInt(), anyString())).thenReturn(pagina);

        mockMvc.perform(get("/api/ventas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(1L))
                .andExpect(jsonPath("$.contenido[0].total").value(0.40));
    }

    @Test
    void obtenerPorId_cuandoExiste_devuelve200() throws Exception {
        VentaResponse venta = new VentaResponse(10L, OffsetDateTime.now(), 1L,
                List.of(new ItemVentaResponse(1L, 1L, "Ibuprofeno 400 mg", "Unidad", new BigDecimal("0.50"), 1, "BUSQUEDA")),
                new BigDecimal("0.42"), new BigDecimal("0.08"), new BigDecimal("0.50"), "efectivo", true,
                UUID.randomUUID());
        when(ventaService.obtenerPorId(10L)).thenReturn(venta);

        mockMvc.perform(get("/api/ventas/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.total").value(0.50));
    }

    @Test
    void obtenerPorId_cuandoNoExiste_devuelve404() throws Exception {
        when(ventaService.obtenerPorId(999L)).thenThrow(new VentaNoEncontradaException());

        mockMvc.perform(get("/api/ventas/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("VENTA_NO_ENCONTRADA"));
    }
}
