package com.botica.backend.controller;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.config.JwtAccessDeniedHandler;
import com.botica.backend.config.JwtAuthenticationEntryPoint;
import com.botica.backend.config.SecurityConfig;
import com.botica.backend.dao.NotificacionDao;
import com.botica.backend.service.SseEmitterService;
import com.botica.backend.service.SseTicketService;
import com.botica.backend.util.FechaNegocio;
import com.botica.backend.util.JwtUtil;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T15-02 (H2): el stream SSE se abre con un ticket de un solo uso y 60 s
 * de vida, nunca con el JWT en la URL. Filtro JWT real (no addFilters=false),
 * mismo criterio que {@link SecurityIntegrationTest}.
 */
@WebMvcTest(NotificacionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class, JwtUtil.class, SseTicketService.class, FechaNegocio.class,
        com.botica.backend.service.NotificacionService.class})
class NotificacionTicketSeguridadTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @MockitoBean
    private NotificacionDao notificacionDao;
    @MockitoBean
    private SseEmitterService sseEmitterService;
    @MockitoBean
    private ContextoOperacion contexto;

    @Test
    void pedirTicket_sinCabecera_devuelve401() throws Exception {
        mockMvc.perform(post("/api/notificaciones/ticket"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ticket_abreElStream_unaSolaVez() throws Exception {
        when(sseEmitterService.crearConexion(any(), any(), any())).thenReturn(new SseEmitter());
        String ticket = pedirTicket();

        mockMvc.perform(get("/api/notificaciones/stream").param("ticket", ticket))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notificaciones/stream").param("ticket", ticket))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ticket_enOtraRuta_noAutentica() throws Exception {
        String ticket = pedirTicket();

        mockMvc.perform(get("/api/notificaciones").param("ticket", ticket))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtEnLaQuery_delStream_devuelve401() throws Exception {
        mockMvc.perform(get("/api/notificaciones/stream").param("token", tokenDe("TECNICO")))
                .andExpect(status().isUnauthorized());
    }

    private String pedirTicket() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/notificaciones/ticket")
                        .header("Authorization", "Bearer " + tokenDe("TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiraEnSegundos").value(60))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.ticket");
    }

    private String tokenDe(String rol) {
        return jwtUtil.generar(Map.of(
                "sub", "usuario.de.prueba", "usuarioId", 1, "boticaId", 1, "rol", rol, "turno", "Tarde"));
    }
}
