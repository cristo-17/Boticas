package com.botica.backend.controller;

import com.botica.backend.dto.MermaResponse;
import com.botica.backend.dto.NuevaMermaRequest;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.service.MermaService;
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
@RequestMapping("/api/mermas")
public class MermaController {

    private final MermaService mermaService;

    public MermaController(MermaService mermaService) {
        this.mermaService = mermaService;
    }

    // ?fecha=hoy es el único valor soportado hoy (docs/API-CONTRATO.md)
    @GetMapping
    public ResponseEntity<PaginaResponse<MermaResponse>> listar(
            @RequestParam(required = false) String fecha,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(required = false) String orden
    ) {
        return ResponseEntity.ok(mermaService.listarDelDia(pagina, tamano, orden));
    }

    @PostMapping
    public ResponseEntity<MermaResponse> registrar(@Valid @RequestBody NuevaMermaRequest request) {
        return ResponseEntity.ok(mermaService.registrar(request));
    }
}
