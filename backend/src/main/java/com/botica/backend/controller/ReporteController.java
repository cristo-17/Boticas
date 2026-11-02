package com.botica.backend.controller;

import com.botica.backend.dto.ReporteResultadosResponse;
import com.botica.backend.exception.RangoFechasInvalidoException;
import com.botica.backend.service.ReporteService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Endpoints del reporte de resultados y exportación CSV (D-E2-3, Tarea 18).
 * Restringidos en {@code SecurityConfig} exclusivamente a {@code ADMINISTRADOR}.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    @GetMapping("/resultados")
    public ReporteResultadosResponse obtenerResultados(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false, defaultValue = "dia") String agrupar) {
        LocalDate fechaDesde = parsearFecha(desde, "desde");
        LocalDate fechaHasta = parsearFecha(hasta, "hasta");
        return reporteService.obtenerResultados(fechaDesde, fechaHasta, agrupar);
    }

    @GetMapping(value = "/resultados/csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportarResultadosCsv(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false, defaultValue = "dia") String agrupar) {
        LocalDate fechaDesde = parsearFecha(desde, "desde");
        LocalDate fechaHasta = parsearFecha(hasta, "hasta");
        String csv = reporteService.generarCsvResultados(fechaDesde, fechaHasta, agrupar);
        String filename = String.format("reporte-resultados-%s-a-%s.csv", fechaDesde, fechaHasta);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static LocalDate parsearFecha(String raw, String nombreParametro) {
        if (raw == null || raw.isBlank()) {
            throw new RangoFechasInvalidoException("El parámetro '" + nombreParametro + "' es obligatorio (YYYY-MM-DD)");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new RangoFechasInvalidoException("Formato inválido para '" + nombreParametro + "' (use YYYY-MM-DD)");
        }
    }
}
