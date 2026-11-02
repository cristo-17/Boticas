package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.config.JwtAccessDeniedHandler;
import com.botica.backend.config.JwtAuthenticationEntryPoint;
import com.botica.backend.config.SecurityConfig;
import com.botica.backend.service.CatalogoProductoService;
import com.botica.backend.service.ProductoService;
import com.botica.backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R17 con el filtro JWT real (igual que SecurityIntegrationTest): el catálogo
 * es solo del ADMINISTRADOR; las búsquedas de mostrador siguen abiertas a
 * ambos roles. Un 403 no llega al Service.
 */
@WebMvcTest(ProductoController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JwtUtil.class,
        com.botica.backend.service.SseTicketService.class, com.botica.backend.util.FechaNegocio.class})
class ProductoCatalogoSeguridadTest {

    private static final String CUERPO = """
            {"nombre": "Paracetamol 500 mg", "categoria": "Analgésicos", "codigoBarras": "775", "unidadNombre": "tableta",
             "presentaciones": [{"etiqueta": "Unidad", "factorConversion": 1, "precio": 0.20}]}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @MockitoBean
    private ProductoService productoService;
    @MockitoBean
    private CatalogoProductoService catalogoService;

    @Test
    void tecnico_noVeElCatalogo_403() throws Exception {
        esperar403(get("/api/productos/catalogo"));
    }

    @Test
    void tecnico_noCreaProductos_403() throws Exception {
        esperar403(post("/api/productos").contentType("application/json").content(CUERPO));
    }

    @Test
    void tecnico_noEditaProductos_403() throws Exception {
        esperar403(put("/api/productos/1").contentType("application/json").content(CUERPO));
    }

    @Test
    void tecnico_noActivaNiDesactivaProductos_403() throws Exception {
        esperar403(patch("/api/productos/1/estado").contentType("application/json").content("{\"activo\": false}"));
    }

    @Test
    void tecnico_sigueBuscandoEnElMostrador_200() throws Exception {
        when(productoService.buscar(anyString(), anyBoolean())).thenReturn(List.of());

        mockMvc.perform(get("/api/productos?buscar=para").header("Authorization", "Bearer " + tokenDe("TECNICO")))
                .andExpect(status().isOk());
    }

    @Test
    void administrador_veElCatalogo_200() throws Exception {
        mockMvc.perform(get("/api/productos/catalogo").header("Authorization", "Bearer " + tokenDe("ADMINISTRADOR")))
                .andExpect(status().isOk());
    }

    private void esperar403(MockHttpServletRequestBuilder peticion) throws Exception {
        mockMvc.perform(peticion.header("Authorization", "Bearer " + tokenDe("TECNICO")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SIN_PERMISO"));
        verifyNoInteractions(catalogoService);
    }

    private String tokenDe(String rol) {
        return jwtUtil.generar(Map.of(
                "sub", "usuario.de.prueba", "usuarioId", 1, "boticaId", 1, "rol", rol, "turno", "Tarde"));
    }
}
