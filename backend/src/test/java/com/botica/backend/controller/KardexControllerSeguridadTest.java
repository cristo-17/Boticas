package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.config.JwtAccessDeniedHandler;
import com.botica.backend.config.JwtAuthenticationEntryPoint;
import com.botica.backend.config.SecurityConfig;
import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.dto.FilaKardexResponse;
import com.botica.backend.dto.KardexResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ResumenKardexResponse;
import com.botica.backend.model.Usuario;
import com.botica.backend.service.KardexService;
import com.botica.backend.service.SseTicketService;
import com.botica.backend.util.FechaNegocio;
import com.botica.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tarea 20 (R17): el Kardex y su .xlsx son solo del ADMINISTRADOR. */
@WebMvcTest(KardexController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class, JwtUtil.class, SseTicketService.class, FechaNegocio.class})
class KardexControllerSeguridadTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @MockitoBean
    private KardexService kardexService;
    @MockitoBean
    private UsuarioDao usuarioDao;

    @BeforeEach
    void usuariosActivosPorDefecto() {
        when(usuarioDao.buscarPorId(1L)).thenReturn(Optional.of(
                Usuario.builder().id(1L).boticaId(1L).usuario("rosa.quispe").rol("TECNICO").activo(true).build()));
        when(usuarioDao.buscarPorId(2L)).thenReturn(Optional.of(
                Usuario.builder().id(2L).boticaId(1L).usuario("carlos.mendoza").rol("ADMINISTRADOR").activo(true).build()));
    }

    @Test
    void tecnico_recibe403_enKardexYEnSuExcel() throws Exception {
        mockMvc.perform(get("/api/kardex?productoId=1").header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SIN_PERMISO"));
        mockMvc.perform(get("/api/kardex/xlsx?productoId=1").header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SIN_PERMISO"));
        verifyNoInteractions(kardexService);
    }

    @Test
    void sinToken_recibe401() throws Exception {
        mockMvc.perform(get("/api/kardex?productoId=1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
        mockMvc.perform(get("/api/kardex/xlsx?productoId=1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
    }

    @Test
    void admin_recibe200_conFilasYExcel() throws Exception {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        var fila = new FilaKardexResponse(9L, OffsetDateTime.parse("2026-10-02T10:00:00-05:00"), "SALIDA_RECETA", false,
                -3, 97L, 4L, "L-01", 20L, null, "R-123", "Rosa Quispe");
        when(kardexService.consultar(7L, desde, null, 0, 50)).thenReturn(new KardexResponse(
                7L, "Amoxicilina 500 mg", "cápsula", true, true, desde, null,
                new ResumenKardexResponse(100, 0, 3, 97, 97, 1),
                PaginaResponse.de(List.of(fila), 0, 50, 2)));

        mockMvc.perform(get("/api/kardex?productoId=7&desde=2026-10-01").header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productoNombre").value("Amoxicilina 500 mg"))
                .andExpect(jsonPath("$.resumen.saldoFinal").value(97))
                .andExpect(jsonPath("$.movimientos.contenido[0].tipo").value("SALIDA_RECETA"))
                .andExpect(jsonPath("$.movimientos.contenido[0].recetaNumero").value("R-123"));

        byte[] xlsx = {0x50, 0x4B, 0x03, 0x04};
        when(kardexService.generarExcel(7L, desde, null)).thenReturn(xlsx);
        byte[] bytes = mockMvc.perform(get("/api/kardex/xlsx?productoId=7&desde=2026-10-01")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"kardex_7_2026-10-01.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(bytes).isEqualTo(xlsx);
    }

    @Test
    void sinProductoId_oConFechaMal_400ConCodigoPropio() throws Exception {
        mockMvc.perform(get("/api/kardex").header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FILTRO_INVALIDO"));
        mockMvc.perform(get("/api/kardex?productoId=abc").header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FILTRO_INVALIDO"));
        mockMvc.perform(get("/api/kardex?productoId=1&desde=01-10-2026").header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("RANGO_FECHAS_INVALIDO"));
        verifyNoInteractions(kardexService);
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
