package com.botica.backend.controller;

import com.botica.backend.config.ContextoOperacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DIF-09 (Tarea 0B): alta de lote con datos que solo frenaba la BD
 * (CHECK/UNIQUE). Contexto completo y BD real, no el controller con el
 * Service mockeado: lo que se prueba es qué llega al cliente cuando la base
 * rechaza el INSERT. @Transactional: rollback automático.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class LoteRegistroIntegracionTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ContextoOperacion contexto;

    private Long productoId;

    @BeforeEach
    void elegirProductoDeLaBotica() {
        productoId = jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? ORDER BY id LIMIT 1", Long.class, contexto.boticaId());
    }

    @Test
    void registrar_conStockNegativo_devuelve400FormatoInvalido() throws Exception {
        mockMvc.perform(post("/api/lotes").contentType("application/json").content(cuerpo("QA-NEG-S", -5, "1.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));
    }

    @Test
    void registrar_conCostoNegativo_devuelve400FormatoInvalido() throws Exception {
        mockMvc.perform(post("/api/lotes").contentType("application/json").content(cuerpo("QA-NEG-C", 10, "-1.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));
    }

    @Test
    void registrar_conCodigoRepetidoParaElMismoProducto_devuelve409LoteDuplicado() throws Exception {
        String codigo = "QA-DUP-" + (System.nanoTime() % 1_000_000_000L);
        mockMvc.perform(post("/api/lotes").contentType("application/json").content(cuerpo(codigo, 10, "1.00")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/lotes").contentType("application/json").content(cuerpo(codigo, 5, "1.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LOTE_DUPLICADO"));
    }

    private String cuerpo(String codigo, int stock, String costo) {
        return """
                {"productoId": %d, "codigo": "%s", "fechaVencimiento": "2027-12-31", "stock": %d, "ubicacion": "A1", "costoUnitario": %s}
                """.formatted(productoId, codigo, stock, costo);
    }
}
