package com.botica.backend.controller;

import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ResolverRevisionRequest;
import com.botica.backend.dto.RevisionVentaResponse;
import com.botica.backend.service.RevisionVentaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tarea 22: "Ventas por revisar". Solo ADMINISTRADOR (SecurityConfig); sin reglas acá (Regla 2). */
@RestController
@RequestMapping("/api/ventas/revisiones")
public class RevisionVentaController {

    private final RevisionVentaService service;

    public RevisionVentaController(RevisionVentaService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PaginaResponse<RevisionVentaResponse>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return ResponseEntity.ok(service.listar(estado, pagina, tamano));
    }

    @PostMapping("/{id}/resolver")
    public ResponseEntity<RevisionVentaResponse> resolver(@PathVariable Long id, @Valid @RequestBody ResolverRevisionRequest request) {
        return ResponseEntity.ok(service.resolver(id, request));
    }
}
