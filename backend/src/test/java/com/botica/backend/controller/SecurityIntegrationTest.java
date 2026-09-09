package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.config.JwtAccessDeniedHandler;
import com.botica.backend.config.JwtAuthenticationEntryPoint;
import com.botica.backend.config.SecurityConfig;
import com.botica.backend.dto.CajaResponse;
import com.botica.backend.dto.CerrarCajaRequest;
import com.botica.backend.service.CajaService;
import com.botica.backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A diferencia de los *ControllerTest normales (@AutoConfigureMockMvc
 * addFilters=false): ACÁ el filtro JWT real SÍ corre. Es la prueba de
 * que SecurityConfig hace lo que dice — 401 sin token, 403 con el rol
 * equivocado, 200 con el correcto — no una simulación.
 * JwtUtil real (no mockeado): genera tokens de verdad, firmados con el
 * secreto de test (app.jwt.secret, application-test.properties).
 */
@WebMvcTest(CajaController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JwtUtil.class})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @MockitoBean
    private CajaService cajaService;

    @Test
    void endpointProtegido_sinToken_devuelve401NoAutenticado() throws Exception {
        mockMvc.perform(get("/api/caja/hoy"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
    }

    @Test
    void endpointProtegido_conTokenInvalido_devuelve401NoAutenticado() throws Exception {
        mockMvc.perform(get("/api/caja/hoy").header("Authorization", "Bearer esto-no-es-un-jwt-valido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
    }

    @Test
    void endpointProtegido_conTokenValidoDeTecnico_dejaPasar() throws Exception {
        when(cajaService.obtenerCajaDeHoy()).thenReturn(cajaResponseAbierta());

        mockMvc.perform(get("/api/caja/hoy").header("Authorization", "Bearer " + tokenDe("TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioNombre").value("Rosa Quispe"));
    }

    @Test
    void cerrarCaja_conRolTecnico_devuelve403SinPermiso() throws Exception {
        // Demostrable (Tarea 12): solo ADMINISTRADOR cierra caja -- un TECNICO con token
        // perfectamente válido igual se queda afuera de ESTE endpoint puntual.
        mockMvc.perform(post("/api/caja/cerrar")
                        .header("Authorization", "Bearer " + tokenDe("TECNICO"))
                        .contentType("application/json")
                        .content("""
                                {"montoContado": 100.00}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SIN_PERMISO"));
    }

    @Test
    void cerrarCaja_conRolAdministrador_dejaPasarLaSeguridad() throws Exception {
        when(cajaService.cerrar(any(CerrarCajaRequest.class))).thenReturn(cajaResponseAbierta());

        mockMvc.perform(post("/api/caja/cerrar")
                        .header("Authorization", "Bearer " + tokenDe("ADMINISTRADOR"))
                        .contentType("application/json")
                        .content("""
                                {"montoContado": 100.00}
                                """))
                .andExpect(status().isOk());
    }

    private String tokenDe(String rol) {
        return jwtUtil.generar(Map.of(
                "sub", "usuario.de.prueba", "usuarioId", 1, "boticaId", 1, "rol", rol, "turno", "Tarde"));
    }

    private CajaResponse cajaResponseAbierta() {
        return new CajaResponse(
                1L, LocalDate.now(), 1L, "Rosa Quispe", "Tarde",
                new BigDecimal("100.00"), OffsetDateTime.now(), null,
                null, null, null, null, null, true
        );
    }
}
