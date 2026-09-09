package com.botica.backend.controller;

import com.botica.backend.dto.AlertaResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ResumenDashboardResponse;
import com.botica.backend.service.AlertaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No valida reglas de negocio ni toca la base de datos (Regla 2): recibe
 * la petición, llama al Service, devuelve la respuesta.
 */
@RestController
public class AlertaController {

    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @GetMapping("/api/dashboard/resumen")
    public ResponseEntity<ResumenDashboardResponse> resumen() {
        return ResponseEntity.ok(alertaService.resumen());
    }

    // Sin ?orden=: el orden (urgencia y dinero en riesgo) es una regla de negocio fija, no una preferencia del cliente.
    @GetMapping("/api/alertas")
    public ResponseEntity<PaginaResponse<AlertaResponse>> listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return ResponseEntity.ok(alertaService.listar(pagina, tamano));
    }
}
