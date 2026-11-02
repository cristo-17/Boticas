package com.botica.backend.config;

import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.model.Usuario;
import com.botica.backend.service.SseTicketService;
import com.botica.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lee "Authorization: Bearer <token>" en cada petición. Si el token es
 * válido, verifica en la base de datos que el usuario siga activo y que
 * su rol coincida con el del token (Tarea 17: desactivar a un usuario o
 * cambiarle el rol invalida su token en la siguiente petición), y deja el
 * {@link JwtPrincipal} en el SecurityContext -- si no hay token, es
 * inválido/expirado, el usuario fue desactivado o cambió de rol, sigue la
 * cadena SIN autenticar (deja que authorizeHttpRequests decida: público
 * sigue pasando, protegido lo rechaza el {@link JwtAuthenticationEntryPoint}).
 * El JWT NUNCA se lee de la query (T15-02): la única credencial por URL
 * es el ticket de {@link SseTicketService}, y solo en {@link #RUTA_STREAM}.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String RUTA_STREAM = "/api/notificaciones/stream";

    private final JwtUtil jwtUtil;
    private final SseTicketService sseTicketService;
    private final ObjectProvider<UsuarioDao> usuarioDaoProvider;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   SseTicketService sseTicketService,
                                   ObjectProvider<UsuarioDao> usuarioDaoProvider) {
        this.jwtUtil = jwtUtil;
        this.sseTicketService = sseTicketService;
        this.usuarioDaoProvider = usuarioDaoProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Map<String, Object> claims = jwtUtil.verificarYExtraer(header.substring(7));
                JwtPrincipal principal = new JwtPrincipal(
                        ((Number) claims.get("usuarioId")).longValue(),
                        ((Number) claims.get("boticaId")).longValue(),
                        (String) claims.get("rol"),
                        (String) claims.get("turno"),
                        (String) claims.get("sub"));
                if (sigueVigenteEnBase(principal)) {
                    autenticar(principal);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (RuntimeException e) {
                SecurityContextHolder.clearContext();
            }
        } else if (RUTA_STREAM.equals(request.getRequestURI()) && request.getParameter("ticket") != null) {
            sseTicketService.canjear(request.getParameter("ticket"))
                    .filter(this::sigueVigenteEnBase)
                    .ifPresent(this::autenticar);
        }
        chain.doFilter(request, response);
    }

    /**
     * Un usuario desactivado (buscarPorId filtra activo = true) o cuyo rol
     * en la base ya no coincide con el del token pierde acceso en la
     * siguiente petición (Tarea 17).
     */
    private boolean sigueVigenteEnBase(JwtPrincipal principal) {
        UsuarioDao usuarioDao = usuarioDaoProvider.getIfAvailable();
        if (usuarioDao == null) {
            return true;
        }
        Optional<Usuario> actual = usuarioDao.buscarPorId(principal.usuarioId());
        return actual.isPresent()
                && actual.get().getBoticaId().equals(principal.boticaId())
                && actual.get().getRol().equals(principal.rol());
    }

    private void autenticar(JwtPrincipal principal) {
        List<GrantedAuthority> autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + principal.rol()));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, autoridades);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
