package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.dto.AlertaResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ResumenDashboardResponse;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.service.AlertaService;
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
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertaController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AlertaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private com.botica.backend.util.JwtUtil jwtUtil;

    @MockitoBean
    private AlertaService alertaService;

    @Test
    void resumen_devuelveNumerosCrudos_sinMontoEsperadoDeCaja() throws Exception {
        when(alertaService.resumen()).thenReturn(new ResumenDashboardResponse(
                new BigDecimal("2964.50"), 12, 64L, 14L, 5L, 6L, 2L,
                CajaDiaria.ABIERTA, OffsetDateTime.parse("2026-09-08T08:02:00-05:00")));

        mockMvc.perform(get("/api/dashboard/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ventasHoy").value(2964.50))
                .andExpect(jsonPath("$.ventasHoyVariacionPct").value(12))
                .andExpect(jsonPath("$.cajaEstado").value("ABIERTA"))
                .andExpect(jsonPath("$.montoEsperado").doesNotExist());
    }

    @Test
    void listar_devuelvePaginaResponseDelService() throws Exception {
        AlertaResponse alerta = new AlertaResponse("venc-6", "urgente", "Omeprazol 20 mg · lote L-2311D vencido",
                "7 unidades vencieron el 25/08 en B-2. Retíralas y regístralas como merma.", "S/ 80.50",
                "Retirar lote", "/inventario", Map.of("vencimiento", "vencido"));
        when(alertaService.listar(0, 20)).thenReturn(PaginaResponse.de(List.of(alerta), 0, 20, 1));

        mockMvc.perform(get("/api/alertas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value("venc-6"))
                .andExpect(jsonPath("$.contenido[0].nivel").value("urgente"))
                .andExpect(jsonPath("$.contenido[0].accionQueryParams.vencimiento").value("vencido"))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }
}
