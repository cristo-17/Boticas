package com.botica.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Tarea 12: reemplaza el modo desarrollo (permitAll) por JWT real.
 * Stateless (sin sesión de servidor) — la identidad viaja completa en
 * el token, validado por {@link JwtAuthenticationFilter} antes de
 * llegar acá.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                           JwtAuthenticationEntryPoint authenticationEntryPoint,
                           JwtAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Preflight de CORS: nunca pasa por el filtro de JWT (el navegador no manda Authorization en un OPTIONS).
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // GET /api/auth/yo es Usuario|null (docs/API-CONTRATO.md): pasa sin token (responde null),
                        // AuthController decide el cuerpo -- no es un endpoint protegido.
                        .requestMatchers(HttpMethod.GET, "/api/auth/yo").permitAll()
                        // GET /api/config: sin autenticar (Tarea 8) -- el frontend lo pide antes de que exista sesión.
                        .requestMatchers(HttpMethod.GET, "/api/config").permitAll()
                        // Demostrable (Tarea 12): solo ADMINISTRADOR cierra caja.
                        .requestMatchers(HttpMethod.POST, "/api/caja/cerrar").hasRole("ADMINISTRADOR")
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
