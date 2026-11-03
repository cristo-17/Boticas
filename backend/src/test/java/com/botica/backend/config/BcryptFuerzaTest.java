package com.botica.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea D: backend/tools/GenerarHashDemo.java genera DEMO_PASSWORD_HASH con
 * $2a y 10 rondas, lo mismo que el encoder del backend (SecurityConfig). Si
 * alguien cambia la fuerza del encoder, este test falla y recuerda cambiar
 * también la utilidad.
 */
class BcryptFuerzaTest {

    @Test
    void elEncoderDelBackendUsa2aCon10Rondas() {
        assertThat(new SecurityConfig(null, null, null).passwordEncoder().encode("x")).startsWith("$2a$10$");
    }
}
