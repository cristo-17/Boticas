package com.botica.backend.controller;

import com.botica.backend.dto.KardexResponse;
import com.botica.backend.exception.FiltroInvalidoException;
import com.botica.backend.exception.RangoFechasInvalidoException;
import com.botica.backend.service.KardexService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Kardex por producto (Tarea 20, D-E2-5). Restringido en {@code SecurityConfig}
 * a {@code ADMINISTRADOR}. Los parámetros llegan como texto y se validan aquí
 * para responder 400 con código propio (un tipo inválido daría 500 genérico).
 */
@RestController
@RequestMapping("/api/kardex")
public class KardexController {

    private final KardexService kardexService;

    public KardexController(KardexService kardexService) {
        this.kardexService = kardexService;
    }

    @GetMapping
    public KardexResponse consultar(
            @RequestParam(required = false) String productoId,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamano) {
        return kardexService.consultar(parsearProducto(productoId), parsearFecha(desde, "desde"),
                parsearFecha(hasta, "hasta"), pagina, tamano);
    }

    @GetMapping("/xlsx")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) String productoId,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        Long id = parsearProducto(productoId);
        LocalDate fechaDesde = parsearFecha(desde, "desde");
        LocalDate fechaHasta = parsearFecha(hasta, "hasta");
        byte[] xlsx = kardexService.generarExcel(id, fechaDesde, fechaHasta);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + KardexService.nombreArchivoExcel(id, fechaDesde, fechaHasta) + "\"")
                .contentType(ReporteController.MEDIA_XLSX)
                .body(xlsx);
    }

    private static Long parsearProducto(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new FiltroInvalidoException("productoId", "");
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new FiltroInvalidoException("productoId", raw);
        }
    }

    /** Opcional: vacío = sin límite por ese lado. */
    private static LocalDate parsearFecha(String raw, String nombre) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new RangoFechasInvalidoException("Formato inválido para '" + nombre + "' (use YYYY-MM-DD)");
        }
    }
}
