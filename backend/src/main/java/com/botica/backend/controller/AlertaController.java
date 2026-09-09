package com.botica.backend.controller;

import com.botica.backend.dto.AlertaResponse;
import com.botica.backend.dto.ResumenDashboardResponse;
import com.botica.backend.service.AlertaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alertas")
public class AlertaController {

    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @GetMapping
    public ResponseEntity<List<AlertaResponse>> listar() {
        return ResponseEntity.ok(alertaService.listarAlertas());
    }

    @GetMapping("/resumen")
    public ResponseEntity<ResumenDashboardResponse> obtenerResumen() {
        return ResponseEntity.ok(alertaService.obtenerResumen());
    }
}
