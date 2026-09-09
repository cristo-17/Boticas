package com.botica.backend.util;

import com.botica.backend.exception.TokenInvalidoException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT hecho a mano (sin librería de terceros, ver JwtUtil) — estas
 * pruebas son la verificación real de que la firma HS256 protege el
 * token: nadie debería poder cambiar un claim sin invalidar la firma.
 */
class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil(new ObjectMapper(), "secreto-de-test-bien-largo-1234567890", 12);

    @Test
    void generarYVerificar_devuelveLosMismosClaimsQueSeMandaron() {
        String token = jwtUtil.generar(Map.of("usuarioId", 1, "boticaId", 1, "rol", "TECNICO", "turno", "Tarde", "sub", "rosa.quispe"));

        Map<String, Object> claims = jwtUtil.verificarYExtraer(token);

        assertThat(claims.get("rol")).isEqualTo("TECNICO");
        assertThat(claims.get("sub")).isEqualTo("rosa.quispe");
        assertThat(claims).containsKeys("iat", "exp");
    }

    @Test
    void verificarYExtraer_conFirmaAlterada_lanzaTokenInvalido() {
        String token = jwtUtil.generar(Map.of("usuarioId", 1, "boticaId", 1, "rol", "TECNICO", "turno", "Tarde", "sub", "rosa.quispe"));
        String[] partes = token.split("\\.");
        // Cambia un carácter de la firma -- simula un token manipulado.
        String firmaAlterada = (partes[2].charAt(0) == 'a' ? 'b' : 'a') + partes[2].substring(1);
        String tokenManipulado = partes[0] + "." + partes[1] + "." + firmaAlterada;

        assertThatThrownBy(() -> jwtUtil.verificarYExtraer(tokenManipulado))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void verificarYExtraer_conOtroSecreto_lanzaTokenInvalido() {
        String token = jwtUtil.generar(Map.of("usuarioId", 1, "boticaId", 1, "rol", "TECNICO", "turno", "Tarde", "sub", "rosa.quispe"));
        JwtUtil otroServicio = new JwtUtil(new ObjectMapper(), "otro-secreto-completamente-distinto-0987654321", 12);

        assertThatThrownBy(() -> otroServicio.verificarYExtraer(token))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void verificarYExtraer_conTokenYaExpirado_lanzaTokenInvalido() {
        // -1 hora: exp queda una hora ANTES de iat, sin depender de que el test corra en el mismo segundo que la expiración (0 horas sería un borde inestable: exp == iat).
        JwtUtil expiraYa = new JwtUtil(new ObjectMapper(), "secreto-de-test-bien-largo-1234567890", -1);
        String token = expiraYa.generar(Map.of("usuarioId", 1, "boticaId", 1, "rol", "TECNICO", "turno", "Tarde", "sub", "rosa.quispe"));

        assertThatThrownBy(() -> expiraYa.verificarYExtraer(token))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void verificarYExtraer_conFormatoRoto_lanzaTokenInvalido() {
        assertThatThrownBy(() -> jwtUtil.verificarYExtraer("esto-no-es-un-jwt"))
                .isInstanceOf(TokenInvalidoException.class);
    }
}
