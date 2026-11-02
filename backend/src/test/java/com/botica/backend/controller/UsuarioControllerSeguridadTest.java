package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.config.JwtAccessDeniedHandler;
import com.botica.backend.config.JwtAuthenticationEntryPoint;
import com.botica.backend.config.SecurityConfig;
import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.UsuarioAdminResponse;
import com.botica.backend.model.Usuario;
import com.botica.backend.service.SseTicketService;
import com.botica.backend.service.UsuarioService;
import com.botica.backend.util.FechaNegocio;
import com.botica.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tarea 17, Bloque B:
 * <ul>
 *   <li>R17: las 5 rutas de /api/usuarios responden 403 SIN_PERMISO al TECNICO.</li>
 *   <li>password_hash nunca sale en ninguna respuesta JSON.</li>
 *   <li>Validación de contraseña nueva (mínimo 8 caracteres → 400 FORMATO_INVALIDO).</li>
 *   <li>Un usuario desactivado o cuyo rol cambió en la base recibe 401 NO_AUTENTICADO
 *       con su token viejo en la siguiente petición.</li>
 * </ul>
 */
@WebMvcTest(UsuarioController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class, JwtUtil.class, SseTicketService.class, FechaNegocio.class})
class UsuarioControllerSeguridadTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private UsuarioDao usuarioDao;

    @BeforeEach
    void usuariosActivosEnBasePorDefecto() {
        when(usuarioDao.buscarPorId(1L)).thenReturn(Optional.of(
                Usuario.builder().id(1L).boticaId(1L).usuario("rosa.quispe").rol("TECNICO").activo(true).build()));
        when(usuarioDao.buscarPorId(2L)).thenReturn(Optional.of(
                Usuario.builder().id(2L).boticaId(1L).usuario("carlos.mendoza").rol("ADMINISTRADOR").activo(true).build()));
    }

    @Test
    void tecnico_noListaUsuarios_403() throws Exception {
        esperar403(get("/api/usuarios"));
    }

    @Test
    void tecnico_noCreaUsuarios_403() throws Exception {
        esperar403(post("/api/usuarios").contentType("application/json")
                .content("{\"nombre\":\"Lucía\",\"usuario\":\"lucia\",\"password\":\"clave1234\",\"rol\":\"TECNICO\"}"));
    }

    @Test
    void tecnico_noEditaUsuarios_403() throws Exception {
        esperar403(patch("/api/usuarios/1").contentType("application/json")
                .content("{\"nombre\":\"Rosa\",\"rol\":\"TECNICO\",\"activo\":true}"));
    }

    @Test
    void tecnico_noRestableceClave_403() throws Exception {
        esperar403(post("/api/usuarios/1/restablecer-clave").contentType("application/json")
                .content("{\"password\":\"clave1234\"}"));
    }

    @Test
    void tecnico_noDesbloqueaUsuarios_403() throws Exception {
        esperar403(post("/api/usuarios/1/desbloquear"));
    }

    @Test
    void jsonDeUsuarios_nuncaContienePasswordHashNiPassword() throws Exception {
        UsuarioAdminResponse fila = new UsuarioAdminResponse(
                1L, "Rosa Quispe", "rosa.quispe", "TECNICO", true, 0, null, false, false, OffsetDateTime.now());
        when(usuarioService.listar(any(), anyInt(), anyInt(), any()))
                .thenReturn(new PaginaResponse<>(List.of(fila), 0, 20, 1, 1));
        when(usuarioService.crear(any())).thenReturn(fila);

        String jsonLista = mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(jsonLista).doesNotContain("passwordHash").doesNotContain("password_hash").doesNotContain("password");

        String jsonCreado = mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin())
                        .contentType("application/json")
                        .content("{\"nombre\":\"Rosa\",\"usuario\":\"rosa.q\",\"password\":\"clave1234\",\"rol\":\"TECNICO\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(jsonCreado).doesNotContain("passwordHash").doesNotContain("password_hash").doesNotContain("password");
    }

    @Test
    void passwordMenorA8Caracteres_enAltaYEnRestablecer_devuelve400FormatoInvalido() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin())
                        .contentType("application/json")
                        .content("{\"nombre\":\"Lucía\",\"usuario\":\"lucia\",\"password\":\"corta12\",\"rol\":\"TECNICO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));

        mockMvc.perform(post("/api/usuarios/1/restablecer-clave")
                        .header("Authorization", "Bearer " + tokenAdmin())
                        .contentType("application/json")
                        .content("{\"password\":\"1234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));
        verifyNoInteractions(usuarioService);
    }

    @Test
    void usuarioDesactivado_suTokenVigenteDejaDeServirEnLaSiguientePeticion_401() throws Exception {
        String tokenViejo = tokenAdmin();
        when(usuarioDao.buscarPorId(2L)).thenReturn(Optional.empty()); // buscarPorId filtra activo = true

        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + tokenViejo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
        verifyNoInteractions(usuarioService);
    }

    @Test
    void administradorDegradadoATecnico_suTokenViejoDeAdminYaNoEntraARutasDeAdmin_401() throws Exception {
        // Precisión 2: el token todavía dice ADMINISTRADOR, pero en la base el usuario pasó a TECNICO.
        String tokenViejoDeAdmin = tokenAdmin();
        when(usuarioDao.buscarPorId(2L)).thenReturn(Optional.of(
                Usuario.builder().id(2L).boticaId(1L).usuario("carlos.mendoza").rol("TECNICO").activo(true).build()));

        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + tokenViejoDeAdmin))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
        verifyNoInteractions(usuarioService);
    }

    private void esperar403(MockHttpServletRequestBuilder peticion) throws Exception {
        mockMvc.perform(peticion.header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SIN_PERMISO"));
        verifyNoInteractions(usuarioService);
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
