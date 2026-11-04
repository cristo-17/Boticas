package com.botica.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA final: un error del cliente nunca es 500 ERROR_INTERNO. Ruta inexistente → 404,
 * método equivocado → 405, parámetro de otro tipo o cuerpo que no es JSON → 400. Contexto
 * completo; sin los filtros de seguridad (con ellos, sin token es 401 antes de llegar aquí).
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class RutasYFormatoIntegracionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rutaInexistente_404RutaNoEncontrada() throws Exception {
        mockMvc.perform(get("/api/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RUTA_NO_ENCONTRADA"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/no-existe"));
    }

    @Test
    void metodoNoPermitido_405() throws Exception {
        mockMvc.perform(delete("/api/config"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METODO_NO_PERMITIDO"));
    }

    @Test
    void idQueNoEsNumero_400FormatoInvalido() throws Exception {
        mockMvc.perform(get("/api/ventas/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));
    }

    @Test
    void cuerpoQueNoEsJson_400FormatoInvalido() throws Exception {
        mockMvc.perform(post("/api/caja/abrir").contentType("text/plain").content("x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));
    }
}
