package com.botica.backend.controller;

import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.service.VentaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping
    public ResponseEntity<PaginaResponse<VentaResponse>> listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(defaultValue = "fecha,desc") String orden
    ) {
        return ResponseEntity.ok(ventaService.listar(pagina, tamano, orden));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VentaResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ventaService.obtenerPorId(id));
    }
}
