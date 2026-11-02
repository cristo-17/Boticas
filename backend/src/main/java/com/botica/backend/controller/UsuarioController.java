package com.botica.backend.controller;

import com.botica.backend.dto.ActualizarUsuarioRequest;
import com.botica.backend.dto.CrearUsuarioRequest;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.RestablecerClaveRequest;
import com.botica.backend.dto.UsuarioAdminResponse;
import com.botica.backend.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administración de usuarios de la botica (Tarea 17, Bloque B). Solo
 * ADMINISTRADOR (R17, exigido en SecurityConfig). No toca la base ni
 * valida reglas de negocio aquí (Regla 2).
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<PaginaResponse<UsuarioAdminResponse>> listar(
            @RequestParam(name = "buscar", required = false, defaultValue = "") String buscar,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(required = false) String orden
    ) {
        return ResponseEntity.ok(usuarioService.listar(buscar, pagina, tamano, orden));
    }

    @PostMapping
    public ResponseEntity<UsuarioAdminResponse> crear(@Valid @RequestBody CrearUsuarioRequest request) {
        return ResponseEntity.ok(usuarioService.crear(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UsuarioAdminResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarUsuarioRequest request
    ) {
        return ResponseEntity.ok(usuarioService.actualizar(id, request));
    }

    @PostMapping("/{id}/restablecer-clave")
    public ResponseEntity<UsuarioAdminResponse> restablecerClave(
            @PathVariable Long id,
            @Valid @RequestBody RestablecerClaveRequest request
    ) {
        return ResponseEntity.ok(usuarioService.restablecerClave(id, request));
    }

    @PostMapping("/{id}/desbloquear")
    public ResponseEntity<UsuarioAdminResponse> desbloquear(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.desbloquear(id));
    }
}
