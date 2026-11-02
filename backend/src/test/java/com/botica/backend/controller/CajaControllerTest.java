package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.dto.CajaResponse;
import com.botica.backend.dto.ResumenCierreResponse;
import com.botica.backend.exception.CajaNoAbiertaException;
import com.botica.backend.exception.CajaYaAbiertaException;
import com.botica.backend.service.CajaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CajaController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class CajaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private com.botica.backend.util.JwtUtil jwtUtil;

    @MockitoBean
    private com.botica.backend.service.SseTicketService sseTicketService;

    @MockitoBean
    private CajaService cajaService;

    @Test
    void abrir_exito_devuelve200ConLaCajaCreada() throws Exception {
        CajaResponse response = cajaResponseAbierta();
        when(cajaService.abrir(any())).thenReturn(response);

        mockMvc.perform(post("/api/caja/abrir")
                        .contentType("application/json")
                        .content("""
                                {"montoInicial": 100.00, "turno": "Tarde"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abierta").value(true))
                .andExpect(jsonPath("$.montoApertura").value(100.00));
    }

    @Test
    void abrir_conCajaYaAbierta_devuelve409ConElFormatoDeErrorAcordado() throws Exception {
        when(cajaService.abrir(any())).thenThrow(new CajaYaAbiertaException());

        mockMvc.perform(post("/api/caja/abrir")
                        .contentType("application/json")
                        .content("""
                                {"montoInicial": 100.00, "turno": "Tarde"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CAJA_YA_ABIERTA"))
                .andExpect(jsonPath("$.path").value("/api/caja/abrir"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void abrir_sinMontoInicial_devuelve400FormatoInvalido_noUnStacktrace() throws Exception {
        mockMvc.perform(post("/api/caja/abrir")
                        .contentType("application/json")
                        .content("""
                                {"turno": "Tarde"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));
    }

    // DIF-12 (Tarea 0B): un turno fuera de la lista llegaba al CHECK de caja_diaria y salía como 500.
    @Test
    void abrir_conTurnoFueraDeLaLista_devuelve400FormatoInvalido_noUn500() throws Exception {
        mockMvc.perform(post("/api/caja/abrir")
                        .contentType("application/json")
                        .content("""
                                {"montoInicial": 100.00, "turno": "Madrugada"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value("turno: El turno debe ser Mañana, Tarde o Noche"));
    }

    @Test
    void cerrar_sinCajaAbierta_devuelve409CajaNoAbierta() throws Exception {
        when(cajaService.cerrar(any())).thenThrow(new CajaNoAbiertaException());

        mockMvc.perform(post("/api/caja/cerrar")
                        .contentType("application/json")
                        .content("""
                                {"montoContado": 100.00}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CAJA_NO_ABIERTA"));
    }

    @Test
    void resumenCierre_nuncaIncluyeMontoEsperadoNiDiferenciaNiSemaforo_conteoCiego() throws Exception {
        ResumenCierreResponse resumen = new ResumenCierreResponse(new BigDecimal("340.00"), 52L, 4L);
        when(cajaService.obtenerResumenCierre(1L)).thenReturn(resumen);

        mockMvc.perform(get("/api/caja/1/resumen-cierre"))
                .andExpect(status().isOk())
                // DIF-10 (Tarea 0B): el efectivo vendido + la apertura es casi el esperado; no viaja antes del conteo.
                .andExpect(jsonPath("$.totalVentasEfectivo").doesNotExist())
                .andExpect(jsonPath("$.totalVentasDigital").value(340.00))
                .andExpect(jsonPath("$.cantidadVentas").value(52))
                .andExpect(jsonPath("$.montoEsperado").doesNotExist())
                .andExpect(jsonPath("$.diferencia").doesNotExist())
                .andExpect(jsonPath("$.semaforoDescuadre").doesNotExist());
    }

    private CajaResponse cajaResponseAbierta() {
        return new CajaResponse(
                1L, LocalDate.now(), 1L, "Rosa Quispe", "Tarde",
                new BigDecimal("100.00"), OffsetDateTime.now(), null,
                null, null, null, null, null, true
        );
    }
}
