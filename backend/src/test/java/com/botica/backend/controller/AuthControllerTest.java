package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.config.SecurityConfig;
import com.botica.backend.dto.LoginRequest;
import com.botica.backend.dto.LoginResponse;
import com.botica.backend.dto.UsuarioResponse;
import com.botica.backend.exception.CredencialesInvalidasException;
import com.botica.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void login_credencialesValidas_devuelve200ConTokenYUsuario() throws Exception {
        UsuarioResponse usuario = new UsuarioResponse(
                1L, "Rosa Quispe", "rosa.quispe", "TECNICO", "Tarde", "Botica San Lucas · Av. Grau 412"
        );
        LoginResponse response = new LoginResponse("mocked.jwt.token", usuario);
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usuario": "rosa.quispe",
                                  "password": "password123",
                                  "turno": "Tarde"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mocked.jwt.token"))
                .andExpect(jsonPath("$.usuario.id").value(1))
                .andExpect(jsonPath("$.usuario.nombre").value("Rosa Quispe"))
                .andExpect(jsonPath("$.usuario.rol").value("TECNICO"))
                .andExpect(jsonPath("$.usuario.turno").value("Tarde"));
    }

    @Test
    void login_credencialesInvalidas_devuelve401() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usuario": "rosa.quispe",
                                  "password": "incorrect_password",
                                  "turno": "Tarde"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void login_cuerpoInvalido_devuelve400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usuario": "",
                                  "password": "",
                                  "turno": "Invalido"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void yo_autenticado_devuelve200ConUsuario() throws Exception {
        UsuarioResponse usuario = new UsuarioResponse(
                1L, "Rosa Quispe", "rosa.quispe", "TECNICO", "Tarde", "Botica San Lucas · Av. Grau 412"
        );
        when(authService.obtenerUsuarioActual()).thenReturn(Optional.of(usuario));

        mockMvc.perform(get("/api/auth/yo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.usuario").value("rosa.quispe"))
                .andExpect(jsonPath("$.rol").value("TECNICO"));
    }

    @Test
    void logout_devuelve204() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }
}
