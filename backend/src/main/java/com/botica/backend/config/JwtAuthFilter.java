package com.botica.backend.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired(required = false)
    private JwtUtil jwtUtil;

    public JwtAuthFilter() {}

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (request.getParameter("token") != null) {
            token = request.getParameter("token");
        }

        if (jwtUtil != null && token != null && jwtUtil.esTokenValido(token)) {
            Claims claims = jwtUtil.extraerClaims(token);
                Number usuarioIdNum = claims.get("usuarioId", Number.class);
                Long usuarioId = usuarioIdNum != null ? usuarioIdNum.longValue() : Long.valueOf(claims.getSubject());
                String usuario = claims.get("usuario", String.class);
                String rol = claims.get("rol", String.class);
                String turno = claims.get("turno", String.class);
                Number boticaIdNum = claims.get("boticaId", Number.class);
                Long boticaId = boticaIdNum != null ? boticaIdNum.longValue() : null;

                UsuarioPrincipal principal = new UsuarioPrincipal(usuarioId, usuario, rol, turno, boticaId);
                List<SimpleGrantedAuthority> authorities = rol != null
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + rol))
                        : List.of();

                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

        filterChain.doFilter(request, response);
    }
}
