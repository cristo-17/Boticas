package com.botica.backend.controller;

import com.botica.backend.dto.MermaResponse;
import com.botica.backend.dto.NuevaMermaRequest;
import com.botica.backend.service.MermaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** No valida reglas de negocio ni toca la base de datos (Regla 2). */
@RestController
@RequestMapping("/api/mermas")
public class MermaController {

    private final MermaService mermaService;

    public MermaController(MermaService mermaService) {
        this.mermaService = mermaService;
    }

    @GetMapping
    public ResponseEntity<List<MermaResponse>> listar() {
        return ResponseEntity.ok(mermaService.listarDelDia());
    }

    @PostMapping
    public ResponseEntity<MermaResponse> registrar(@Valid @RequestBody NuevaMermaRequest request) {
        return ResponseEntity.ok(mermaService.registrar(request));
    }
}
