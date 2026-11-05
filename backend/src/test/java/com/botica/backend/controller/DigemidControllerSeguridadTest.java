package com.botica.backend.controller;

import com.botica.backend.config.GlobalExceptionHandler;
import com.botica.backend.config.JwtAccessDeniedHandler;
import com.botica.backend.config.JwtAuthenticationEntryPoint;
import com.botica.backend.config.SecurityConfig;
import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.dto.FilaDigemidResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ReporteDigemidResponse;
import com.botica.backend.dto.TotalDigemidProductoResponse;
import com.botica.backend.dto.TotalesDigemidResponse;
import com.botica.backend.exception.RangoFechasInvalidoException;
import com.botica.backend.model.Usuario;
import com.botica.backend.service.DigemidService;
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

/** RF-54 (R17): el reporte DIGEMID y su .xlsx son solo del ADMINISTRADOR. */
@WebMvcTest(DigemidController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class, JwtUtil.class, SseTicketService.class, FechaNegocio.class})
class DigemidControllerSeguridadTest {

    private static final String RUTA = "/api/reportes/digemid?desde=2026-11-01&hasta=2026-11-04";
    private static final String RUTA_XLSX = "/api/reportes/digemid/xlsx?desde=2026-11-01&hasta=2026-11-04";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @MockitoBean
    private DigemidService digemidService;
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
    void tecnico_recibe403_enElReporteYEnSuExcel() throws Exception {
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SIN_PERMISO"));
        mockMvc.perform(get(RUTA_XLSX).header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SIN_PERMISO"));
        verifyNoInteractions(digemidService);
    }

    @Test
    void sinToken_recibe401() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
        mockMvc.perform(get(RUTA_XLSX))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
        verifyNoInteractions(digemidService);
    }

    @Test
    void admin_recibe200_conLineasYExcel() throws Exception {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 4);
        var sinLote = new FilaDigemidResponse(9L, OffsetDateTime.parse("2026-11-02T10:00:00-05:00"), 20L, 7L,
                "Tramadol 50 mg", "Blíster x10", 2, "cápsula", null, null, null, "RX-1", "12345", null, null, "Rosa Quispe");
        when(digemidService.consultar(desde, hasta, 0, 50)).thenReturn(new ReporteDigemidResponse(desde, hasta,
                DigemidService.NOTA, PaginaResponse.de(List.of(sinLote), 0, 50, 1),
                List.of(new TotalDigemidProductoResponse(7L, "Tramadol 50 mg", "cápsula", 1, 2)),
                new TotalesDigemidResponse(1, 1, 2)));

        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nota").value(DigemidService.NOTA))
                .andExpect(jsonPath("$.lineas.contenido[0].recetaNumero").value("RX-1"))
                .andExpect(jsonPath("$.lineas.contenido[0].loteId").isEmpty())
                .andExpect(jsonPath("$.totalesPorProducto[0].unidades").value(2))
                .andExpect(jsonPath("$.totales.recetas").value(1));

        byte[] xlsx = {0x50, 0x4B, 0x03, 0x04};
        when(digemidService.generarExcel(desde, hasta)).thenReturn(xlsx);
        byte[] bytes = mockMvc.perform(get(RUTA_XLSX).header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"reporte-digemid_2026-11-01_2026-11-04.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(bytes).isEqualTo(xlsx);
    }

    @Test
    void sinFechas_oConFechaMal_oRangoInvalido_400RangoFechasInvalido() throws Exception {
        mockMvc.perform(get("/api/reportes/digemid?hasta=2026-11-04").header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("RANGO_FECHAS_INVALIDO"));
        mockMvc.perform(get("/api/reportes/digemid/xlsx?desde=01-11-2026&hasta=2026-11-04")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("RANGO_FECHAS_INVALIDO"));
        verifyNoInteractions(digemidService);

        LocalDate desde = LocalDate.of(2026, 11, 5);
        LocalDate hasta = LocalDate.of(2026, 11, 4);
        when(digemidService.consultar(desde, hasta, 0, 50))
                .thenThrow(new RangoFechasInvalidoException("La fecha desde no puede ser mayor que hasta"));
        mockMvc.perform(get("/api/reportes/digemid?desde=2026-11-05&hasta=2026-11-04")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("RANGO_FECHAS_INVALIDO"));
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
