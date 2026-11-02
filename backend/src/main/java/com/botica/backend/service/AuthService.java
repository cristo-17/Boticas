package com.botica.backend.service;

import com.botica.backend.config.JwtPrincipal;
import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.dto.CredencialesLoginRequest;
import com.botica.backend.dto.LoginResponse;
import com.botica.backend.dto.UsuarioResponse;
import com.botica.backend.exception.CredencialesInvalidasException;
import com.botica.backend.exception.CuentaBloqueadaException;
import com.botica.backend.model.Usuario;
import com.botica.backend.util.FechaNegocio;
import com.botica.backend.util.JwtUtil;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Reglas de negocio de autenticación — sin SQL acá (Regla 2). No usa
 * ContextoOperacion (Regla: esa interfaz asume una identidad YA
 * validada): login todavía no tiene una, y yo() debe funcionar incluso
 * SIN token (contrato: Usuario | null, nunca 401) leyendo el
 * SecurityContext directo.
 *
 * DIF-01 (Tarea 17): 5 intentos fallidos seguidos bloquean al usuario
 * durante 15 minutos. El contador se incrementa en SQL, nunca leyendo
 * y escribiendo desde Java.
 */
@Service
public class AuthService {

    public static final int MAX_INTENTOS_FALLIDOS = 5;
    public static final int MINUTOS_BLOQUEO = 15;

    private final UsuarioDao usuarioDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FechaNegocio fechaNegocio;

    public AuthService(UsuarioDao usuarioDao, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, FechaNegocio fechaNegocio) {
        this.usuarioDao = usuarioDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.fechaNegocio = fechaNegocio;
    }

    public LoginResponse login(CredencialesLoginRequest request) {
        Usuario usuario = usuarioDao.buscarPorUsuario(request.usuario())
                .orElseThrow(CredencialesInvalidasException::new);

        OffsetDateTime ahora = fechaNegocio.ahora();
        if (usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(ahora)) {
            throw new CuentaBloqueadaException(usuario.getBloqueadoHasta());
        }

        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            OffsetDateTime finBloqueo = ahora.plusMinutes(MINUTOS_BLOQUEO);
            Usuario actualizado = usuarioDao.registrarIntentoFallido(
                    usuario.getId(), ahora, MAX_INTENTOS_FALLIDOS, finBloqueo).orElse(usuario);
            if (actualizado.getBloqueadoHasta() != null && actualizado.getBloqueadoHasta().isAfter(ahora)) {
                throw new CuentaBloqueadaException(actualizado.getBloqueadoHasta());
            }
            throw new CredencialesInvalidasException();
        }

        if ((usuario.getIntentosFallidos() != null && usuario.getIntentosFallidos() > 0)
                || usuario.getBloqueadoHasta() != null) {
            usuarioDao.reiniciarIntentosYBloqueo(usuario.getId());
        }

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
                .filter(u -> u.getRol().equals(principal.rol()))
                .map(u -> aRespuesta(u, principal.turno()))
                .orElse(null);
    }

    private UsuarioResponse aRespuesta(Usuario usuario, String turno) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getUsuario(),
                usuario.getRol(), turno, usuario.getBoticaNombre(), usuario.getBoticaDireccion());
    }
}
