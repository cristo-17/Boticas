package com.botica.backend.controller;

import com.botica.backend.dto.ReporteDigemidResponse;
import com.botica.backend.service.DigemidService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Reporte interno de dispensación con receta (RF-54). Bajo /api/reportes/**:
 * {@code SecurityConfig} lo deja solo al ADMINISTRADOR. Fechas obligatorias,
 * mismas validaciones que Resultados (400 RANGO_FECHAS_INVALIDO).
 */
@RestController
@RequestMapping("/api/reportes/digemid")
public class DigemidController {

    private final DigemidService digemidService;

    public DigemidController(DigemidService digemidService) {
        this.digemidService = digemidService;
    }

    @GetMapping
    public ReporteDigemidResponse consultar(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamano) {
        return digemidService.consultar(ReporteController.parsearFecha(desde, "desde"),
                ReporteController.parsearFecha(hasta, "hasta"), pagina, tamano);
    }

    @GetMapping("/xlsx")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        LocalDate fechaDesde = ReporteController.parsearFecha(desde, "desde");
        LocalDate fechaHasta = ReporteController.parsearFecha(hasta, "hasta");
        byte[] xlsx = digemidService.generarExcel(fechaDesde, fechaHasta);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + DigemidService.nombreArchivoExcel(fechaDesde, fechaHasta) + "\"")
                .contentType(ReporteController.MEDIA_XLSX)
                .body(xlsx);
    }
}
