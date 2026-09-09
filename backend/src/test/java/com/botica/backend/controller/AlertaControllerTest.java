package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.config.SecurityConfig;
import com.botica.backend.dto.AlertaResponse;
import com.botica.backend.dto.ResumenDashboardResponse;
import com.botica.backend.service.AlertaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertaController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@WithMockUser(roles = "FARMACEUTICO")
class AlertaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertaService alertaService;

    @Test
    void listar_devuelve200ConAlertas() throws Exception {
        AlertaResponse alerta = new AlertaResponse(
                "vencido-1",
                "urgente",
                "Amoxicilina 500 mg · lote L-001 vencido",
                "5 unidades vencieron.",
                "S/ 25.00",
                "Registrar merma",
                "/merma",
                Map.of("loteId", "1")
        );
        when(alertaService.listarAlertas()).thenReturn(List.of(alerta));

        mockMvc.perform(get("/api/alertas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("vencido-1"))
                .andExpect(jsonPath("$[0].nivel").value("urgente"))
                .andExpect(jsonPath("$[0].monto").value("S/ 25.00"));
    }

    @Test
    void obtenerResumen_devuelve200ConKPIs() throws Exception {
        ResumenDashboardResponse resumen = new ResumenDashboardResponse(
                "S/ 1250.00",
                "25 boletas registradas hoy",
                3,
                "3 vencen en los próximos 30 días",
                2,
                "2 con unidades por debajo de 15",
                "Abierta",
                "Por Rosa Quispe"
        );
        when(alertaService.obtenerResumen()).thenReturn(resumen);

        mockMvc.perform(get("/api/alertas/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ventasHoyTexto").value("S/ 1250.00"))
                .andExpect(jsonPath("$.cajaEstado").value("Abierta"))
                .andExpect(jsonPath("$.productosPorVencer").value(3));
    }
}
