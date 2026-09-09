package com.botica.backend.controller;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.config.SecurityConfig;
import com.botica.backend.dao.NotificacionDao;
import com.botica.backend.model.Notificacion;
import com.botica.backend.service.SseEmitterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificacionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@WithMockUser(roles = "ADMINISTRADOR")
class NotificacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificacionDao notificacionDao;

    @MockitoBean
    private SseEmitterService sseEmitterService;

    @MockitoBean
    private ContextoOperacion contexto;

    @Test
    void listar_devuelve200ConNotificaciones() throws Exception {
        when(contexto.boticaId()).thenReturn(1L);
        when(contexto.rol()).thenReturn("ADMINISTRADOR");

        Notificacion n = Notificacion.builder()
                .id(1L)
                .boticaId(1L)
                .tipo("DESCUADRE_GRAVE")
                .titulo("Descuadre en caja #1")
                .mensaje("Diferencia de S/ 50.00")
                .leido(false)
                .fechaCreacion(OffsetDateTime.now())
                .build();

        when(notificacionDao.listarRecientes(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(n));

        mockMvc.perform(get("/api/notificaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].tipo").value("DESCUADRE_GRAVE"))
                .andExpect(jsonPath("$[0].leido").value(false));
    }

    @Test
    void marcarComoLeida_devuelve204() throws Exception {
        when(contexto.boticaId()).thenReturn(1L);

        mockMvc.perform(put("/api/notificaciones/1/leer"))
                .andExpect(status().isNoContent());

        verify(notificacionDao).marcarComoLeida(1L, 1L);
    }

    @Test
    void marcarTodasComoLeidas_devuelve204() throws Exception {
        when(contexto.boticaId()).thenReturn(1L);
        when(contexto.rol()).thenReturn("ADMINISTRADOR");

        mockMvc.perform(put("/api/notificaciones/leer-todas"))
                .andExpect(status().isNoContent());

        verify(notificacionDao).marcarTodasComoLeidas(1L, "ADMINISTRADOR");
    }

    @Test
    void conectarStream_devuelve200Sse() throws Exception {
        when(contexto.boticaId()).thenReturn(1L);
        when(contexto.usuarioId()).thenReturn(2L);
        when(contexto.rol()).thenReturn("ADMINISTRADOR");
        when(sseEmitterService.crearConexion(1L, 2L, "ADMINISTRADOR")).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/notificaciones/stream"))
                .andExpect(status().isOk());
    }
}
