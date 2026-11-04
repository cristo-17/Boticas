package com.botica.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF-07: los parámetros de negocio que el frontend carga una vez al iniciar. Contexto completo
 * CON los filtros de seguridad: GET /api/config es público (se pide antes del login).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sinToken_devuelve200_conLosParametrosDelInforme() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.igv").value(0.18))
                .andExpect(jsonPath("$.umbralStockBajo").value(15))
                .andExpect(jsonPath("$.descuadreLeve").value(10.00))
                .andExpect(jsonPath("$.vencimiento.criticoDias").value(30))
                .andExpect(jsonPath("$.vencimiento.advertenciaDias").value(90))
                .andExpect(jsonPath("$.motivosMerma", contains("Vencimiento", "Rotura", "Deterioro", "Robo o pérdida", "Otro")))
                .andExpect(jsonPath("$.motivosQueRequierenObservacion", containsInAnyOrder("Robo o pérdida", "Otro")))
                .andExpect(jsonPath("$.motivosEgreso").isNotEmpty())
                .andExpect(jsonPath("$.version").value("local"));
    }
}
