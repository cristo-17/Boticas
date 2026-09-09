package com.botica.backend.controller;

import com.botica.backend.dto.CredencialesLoginRequest;
import com.botica.backend.dto.LoginResponse;
import com.botica.backend.dto.UsuarioResponse;
import com.botica.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No valida reglas de negocio ni toca la base de datos (Regla 2): recibe
 * la petición, llama al Service, devuelve la respuesta.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody CredencialesLoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // Stateless (sin sesión de servidor, Tarea 12): no hay nada que invalidar acá.
    // Requiere token válido (no está en la lista permitAll de SecurityConfig) --
    // el 401 NO_AUTENTICADO sin token lo arma JwtAuthenticationEntryPoint antes de llegar acá.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/yo")
    public ResponseEntity<UsuarioResponse> yo() {
        return ResponseEntity.ok(authService.yo());
    }
}
