package com.botica.backend.controller;

import com.botica.backend.dto.LoteResponse;
import com.botica.backend.dto.NuevoLoteRequest;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.service.LoteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No valida reglas de negocio ni toca la base de datos (Regla 2): recibe
 * la petición, llama al Service, devuelve la respuesta.
 */
@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private final LoteService loteService;

    public LoteController(LoteService loteService) {
        this.loteService = loteService;
    }

    @GetMapping
    public ResponseEntity<PaginaResponse<LoteResponse>> listar(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String vencimiento,
            @RequestParam(name = "stockBajo", required = false, defaultValue = "false") boolean stockBajo,
            @RequestParam(required = false) Long productoId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(required = false) String orden
    ) {
        return ResponseEntity.ok(loteService.listar(categoria, vencimiento, stockBajo, productoId, pagina, tamano, orden));
    }

    @PostMapping
    public ResponseEntity<LoteResponse> registrar(@Valid @RequestBody NuevoLoteRequest request) {
        return ResponseEntity.ok(loteService.registrar(request));
    }
}
