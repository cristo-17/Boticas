package com.botica.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * MODO DESARROLLO. spring-boot-starter-security está en el pom: sin esta
 * clase, bloquea TODOS los endpoints con una contraseña autogenerada que
 * aparece en el log de arranque (y hace perder una tarde persiguiendo un
 * falso error de CORS). Permite todo bajo este perfil.
 *
 * La Tarea 12 reemplaza este SecurityFilterChain por uno que valida JWT
 * en /api/** salvo /api/auth/** y el endpoint de salud.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
