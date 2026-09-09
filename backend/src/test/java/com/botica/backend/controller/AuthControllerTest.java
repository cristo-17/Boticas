package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.dto.LoginResponse;
import com.botica.backend.dto.UsuarioResponse;
import com.botica.backend.exception.CredencialesInvalidasException;
import com.botica.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false) // filtros de seguridad reales apagados a propósito: esto prueba DTO/error-shape, no auth (Tarea 12) -- ver SecurityIntegrationTest para el filtro real
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    // JwtAuthenticationFilter (Tarea 12) es un Filter, y @WebMvcTest SIEMPRE lo escanea aunque
    // @AutoConfigureMockMvc(addFilters=false) lo deje sin correr -- sin este mock, el contexto
    // no arranca (JwtUtil no está en el slice).
    @MockitoBean
    private com.botica.backend.util.JwtUtil jwtUtil;
    @MockitoBean
    private AuthService authService;

    @Test
    void login_conCredencialesValidas_devuelveTokenYUsuario() throws Exception {
        UsuarioResponse usuario = new UsuarioResponse(1L, "Rosa Quispe", "rosa.quispe", "TECNICO", "Tarde", "Botica San Lucas", "Av. Grau 412");
        when(authService.login(any())).thenReturn(new LoginResponse("token-de-prueba", usuario));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"usuario": "rosa.quispe", "password": "tecnico123", "turno": "Tarde"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-de-prueba"))
                .andExpect(jsonPath("$.usuario.rol").value("TECNICO"))
                .andExpect(jsonPath("$.usuario.id").value(1));
    }

    @Test
    void login_conCredencialesInvalidas_devuelve401ConElFormatoDeErrorAcordado() throws Exception {
        when(authService.login(any())).thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"usuario": "rosa.quispe", "password": "mala", "turno": "Tarde"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void login_sinTurno_devuelve400FormatoInvalido() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"usuario": "rosa.quispe", "password": "tecnico123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FORMATO_INVALIDO"));
    }

    @Test
    void yo_sinUsuarioAutenticado_devuelve200ConCuerpoVacio() throws Exception {
        // ResponseEntity.ok(null) => 200 con cuerpo vacío (sin Content-Type), no el string "null" --
        // Angular (HttpClient) ya trata un cuerpo vacío como null para responseType json, así que
        // esto sigue cumpliendo el contrato "Usuario | null" del lado del cliente.
        when(authService.yo()).thenReturn(null);

        mockMvc.perform(get("/api/auth/yo"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void logout_devuelve204() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }
}
