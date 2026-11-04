package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.config.JwtAccessDeniedHandler;
import com.botica.backend.config.JwtAuthenticationEntryPoint;
import com.botica.backend.config.SecurityConfig;
import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.dto.CatalogoOfflineResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.model.Usuario;
import com.botica.backend.service.CatalogoProductoService;
import com.botica.backend.service.ProductoService;
import com.botica.backend.service.RevisionVentaService;
import com.botica.backend.service.SseTicketService;
import com.botica.backend.util.FechaNegocio;
import com.botica.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tarea 22 (R17): las revisiones son del ADMINISTRADOR; la foto del catálogo, de los dos roles (los dos venden). */
@WebMvcTest({RevisionVentaController.class, ProductoController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class, JwtUtil.class, SseTicketService.class, FechaNegocio.class})
class RevisionVentaControllerSeguridadTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @MockitoBean
    private RevisionVentaService revisionService;
    @MockitoBean
    private ProductoService productoService;
    @MockitoBean
    private CatalogoProductoService catalogoService;
    @MockitoBean
    private UsuarioDao usuarioDao;

    @BeforeEach
    void usuariosActivos() {
        when(usuarioDao.buscarPorId(1L)).thenReturn(Optional.of(
                Usuario.builder().id(1L).boticaId(1L).usuario("rosa.quispe").rol("TECNICO").activo(true).build()));
        when(usuarioDao.buscarPorId(2L)).thenReturn(Optional.of(
                Usuario.builder().id(2L).boticaId(1L).usuario("carlos.mendoza").rol("ADMINISTRADOR").activo(true).build()));
    }

    @Test
    void tecnico_recibe403_alListarYAlResolver() throws Exception {
        mockMvc.perform(get("/api/ventas/revisiones").header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SIN_PERMISO"));
        mockMvc.perform(post("/api/ventas/revisiones/1/resolver").header("Authorization", "Bearer " + tokenTecnico())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accion\":\"VISTA\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SIN_PERMISO"));
        verifyNoInteractions(revisionService);
    }

    @Test
    void administrador_lista() throws Exception {
        when(revisionService.listar(any(), anyInt(), anyInt())).thenReturn(PaginaResponse.de(List.of(), 0, 20, 0L));
        mockMvc.perform(get("/api/ventas/revisiones?estado=PENDIENTE").header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void resolver_conAccionInvalida_400() throws Exception {
        mockMvc.perform(post("/api/ventas/revisiones/1/resolver").header("Authorization", "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accion\":\"APLICAR_LOTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));
    }

    @Test
    void catalogoOffline_loPidenLosDosRoles_yNoEsElCatalogoDeAdministrador() throws Exception {
        when(productoService.catalogoOffline()).thenReturn(new CatalogoOfflineResponse(1L, OffsetDateTime.now(), List.of()));
        mockMvc.perform(get("/api/productos/catalogo-offline").header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boticaId").value(1));
        mockMvc.perform(get("/api/productos/catalogo-offline").header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/productos/catalogo-offline"))
                .andExpect(status().isUnauthorized());
    }

    private String tokenTecnico() {
        return jwtUtil.generar(Map.of(
                "sub", "rosa.quispe", "usuarioId", 1L, "boticaId", 1L, "rol", "TECNICO", "turno", "Tarde"));
    }

    private String tokenAdmin() {
        return jwtUtil.generar(Map.of(
                "sub", "carlos.mendoza", "usuarioId", 2L, "boticaId", 1L, "rol", "ADMINISTRADOR", "turno", "Mañana"));
    }
}
