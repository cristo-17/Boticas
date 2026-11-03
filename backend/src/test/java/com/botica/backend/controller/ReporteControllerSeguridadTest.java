package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.config.JwtAccessDeniedHandler;
import com.botica.backend.config.JwtAuthenticationEntryPoint;
import com.botica.backend.config.SecurityConfig;
import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.dto.FilaResultadoResponse;
import com.botica.backend.dto.ReporteResultadosResponse;
import com.botica.backend.dto.TotalesResultadoResponse;
import com.botica.backend.model.Usuario;
import com.botica.backend.service.ReporteService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
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

@WebMvcTest(ReporteController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class, JwtUtil.class, SseTicketService.class, FechaNegocio.class})
class ReporteControllerSeguridadTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @MockitoBean
    private ReporteService reporteService;
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
    void tecnico_recibe403SinPermiso_enReporteResultadosJson() throws Exception {
        mockMvc.perform(get("/api/reportes/resultados?desde=2026-05-01&hasta=2026-05-10")
                        .header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SIN_PERMISO"));

        verifyNoInteractions(reporteService);
    }

    @Test
    void tecnico_recibe403SinPermiso_enReporteResultadosExcel() throws Exception {
        mockMvc.perform(get("/api/reportes/resultados/xlsx?desde=2026-05-01&hasta=2026-05-10")
                        .header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SIN_PERMISO"));

        verifyNoInteractions(reporteService);
    }

    @Test
    void sinToken_recibe401NoAutenticado_enReporteResultadosJsonYExcel() throws Exception {
        mockMvc.perform(get("/api/reportes/resultados?desde=2026-05-01&hasta=2026-05-10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));

        mockMvc.perform(get("/api/reportes/resultados/xlsx?desde=2026-05-01&hasta=2026-05-10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
    }

    @Test
    void admin_recibe200_enReporteResultadosJsonYExcel() throws Exception {
        LocalDate desde = LocalDate.of(2026, 5, 1);
        LocalDate hasta = LocalDate.of(2026, 5, 2);

        when(reporteService.obtenerResultados(desde, hasta, "dia")).thenReturn(
                new ReporteResultadosResponse(
                        desde, hasta, "dia", ReporteService.NOTA_REPORTE_INTERNO,
                        List.of(new FilaResultadoResponse("2026-05-01", "01/05/2026",
                                new BigDecimal("50.00"), new BigDecimal("30.00"), new BigDecimal("20.00"),
                                BigDecimal.ZERO, new BigDecimal("5.00"), new BigDecimal("35.00"), new BigDecimal("15.00"))),
                        new TotalesResultadoResponse(
                                new BigDecimal("50.00"), new BigDecimal("30.00"), new BigDecimal("20.00"),
                                BigDecimal.ZERO, new BigDecimal("5.00"), new BigDecimal("35.00"), new BigDecimal("15.00"))
                ));

        mockMvc.perform(get("/api/reportes/resultados?desde=2026-05-01&hasta=2026-05-02")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agrupar").value("dia"))
                .andExpect(jsonPath("$.nota").value(ReporteService.NOTA_REPORTE_INTERNO))
                .andExpect(jsonPath("$.filas[0].periodo").value("2026-05-01"))
                .andExpect(jsonPath("$.filas[0].etiqueta").value("01/05/2026"))
                .andExpect(jsonPath("$.filas[0].gastos").value(35.00))
                .andExpect(jsonPath("$.totales.gastos").value(35.00))
                .andExpect(jsonPath("$.totales.resultado").value(15.00));

        byte[] xlsxSimulado = {0x50, 0x4B, 0x03, 0x04};
        when(reporteService.generarExcelResultados(desde, hasta, "dia")).thenReturn(xlsxSimulado);

        byte[] bytes = mockMvc.perform(get("/api/reportes/resultados/xlsx?desde=2026-05-01&hasta=2026-05-02")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"reporte-resultados_2026-05-01_2026-05-02.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(bytes).isEqualTo(xlsxSimulado);
    }

    @Test
    void rutaCsvAnterior_yaNoExiste_paraAdmin() throws Exception {
        mockMvc.perform(get("/api/reportes/resultados/csv?desde=2026-05-01&hasta=2026-05-02")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
        verifyNoInteractions(reporteService);
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
