package com.botica.backend.config;

import com.botica.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Lee "Authorization: Bearer <token>" en cada petición. Si el token es
 * válido, deja el {@link JwtPrincipal} en el SecurityContext -- si no
 * hay token, o es inválido/expirado, sigue la cadena SIN autenticar
 * (deja que authorizeHttpRequests decida: público sigue pasando,
 * protegido lo rechaza el {@link JwtAuthenticationEntryPoint}).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
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
                List<GrantedAuthority> autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + principal.rol()));
                var auth = new UsernamePasswordAuthenticationToken(principal, null, autoridades);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (RuntimeException e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
