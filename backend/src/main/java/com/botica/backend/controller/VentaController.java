package com.botica.backend.controller;

import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.service.VentaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No valida reglas de negocio ni toca la base de datos (Regla 2): recibe
 * la petición, llama al Service, devuelve la respuesta.
 */
@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @PostMapping
    public ResponseEntity<VentaResponse> registrar(@Valid @RequestBody NuevaVentaRequest request) {
        return ResponseEntity.ok(ventaService.registrar(request));
    }
}
