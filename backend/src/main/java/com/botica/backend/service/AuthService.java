package com.botica.backend.service;

import com.botica.backend.config.JwtPrincipal;
import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.dto.CredencialesLoginRequest;
import com.botica.backend.dto.LoginResponse;
import com.botica.backend.dto.UsuarioResponse;
import com.botica.backend.exception.CredencialesInvalidasException;
import com.botica.backend.model.Usuario;
import com.botica.backend.util.JwtUtil;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Reglas de negocio de autenticación — sin SQL acá (Regla 2). No usa
 * ContextoOperacion (Regla: esa interfaz asume una identidad YA
 * validada): login todavía no tiene una, y yo() debe funcionar incluso
 * SIN token (contrato: Usuario | null, nunca 401) leyendo el
 * SecurityContext directo.
 */
@Service
public class AuthService {

    private final UsuarioDao usuarioDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UsuarioDao usuarioDao, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.usuarioDao = usuarioDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(CredencialesLoginRequest request) {
        Usuario usuario = usuarioDao.buscarPorUsuario(request.usuario())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(CredencialesInvalidasException::new);

        String token = jwtUtil.generar(Map.of(
                "sub", usuario.getUsuario(),
                "usuarioId", usuario.getId(),
                "boticaId", usuario.getBoticaId(),
                "rol", usuario.getRol(),
                "turno", request.turno()));

        return new LoginResponse(token, aRespuesta(usuario, request.turno()));
    }

    /** Usuario | null (nunca 401): sin token válido no hay nadie que "seas", no es un error. */
    public UsuarioResponse yo() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof JwtPrincipal principal)) {
            return null;
        }
        return usuarioDao.buscarPorId(principal.usuarioId())
                .map(u -> aRespuesta(u, principal.turno()))
                .orElse(null);
    }

    private UsuarioResponse aRespuesta(Usuario usuario, String turno) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getUsuario(),
                usuario.getRol(), turno, usuario.getBoticaNombre(), usuario.getBoticaDireccion());
    }
}
