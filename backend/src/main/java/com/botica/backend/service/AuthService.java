package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.config.JwtUtil;
import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.dto.LoginRequest;
import com.botica.backend.dto.LoginResponse;
import com.botica.backend.dto.UsuarioResponse;
import com.botica.backend.exception.CredencialesInvalidasException;
import com.botica.backend.model.Usuario;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UsuarioDao usuarioDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ContextoOperacion contextoOperacion;

    public AuthService(UsuarioDao usuarioDao, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, ContextoOperacion contextoOperacion) {
        this.usuarioDao = usuarioDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.contextoOperacion = contextoOperacion;
    }

    public LoginResponse login(LoginRequest req) {
        Usuario usuario = usuarioDao.buscarPorUsuario(req.usuario().trim().toLowerCase())
                .orElseThrow(CredencialesInvalidasException::new);

        if (!passwordEncoder.matches(req.password(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }

        String turnoElegido = req.turno();
        String token = jwtUtil.generarToken(
                usuario.getId(),
                usuario.getUsuario(),
                usuario.getRolNombre(),
                turnoElegido,
                usuario.getBoticaId()
        );

        UsuarioResponse usuarioResponse = construirUsuarioResponse(usuario, turnoElegido);
        return new LoginResponse(token, usuarioResponse);
    }

    public Optional<UsuarioResponse> obtenerUsuarioActual() {
        Long usuarioId = contextoOperacion.usuarioId();
        return usuarioDao.buscarPorId(usuarioId)
                .map(u -> construirUsuarioResponse(u, contextoOperacion.turno()));
    }

    private UsuarioResponse construirUsuarioResponse(Usuario usuario, String turno) {
        String sede = usuario.getBoticaNombre();
        if (usuario.getBoticaDireccion() != null && !usuario.getBoticaDireccion().isBlank()) {
            sede = sede + " · " + usuario.getBoticaDireccion();
        }
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getUsuario(),
                usuario.getRolNombre(),
                turno,
                sede
        );
    }
}
