package com.botica.backend.controller;

import com.botica.backend.dto.ConfigResponse;
import com.botica.backend.service.ConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/config — sin autenticar, se llama una sola vez al iniciar la app (Tarea 9). */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public ResponseEntity<ConfigResponse> obtener() {
        return ResponseEntity.ok(configService.obtener());
    }
}
