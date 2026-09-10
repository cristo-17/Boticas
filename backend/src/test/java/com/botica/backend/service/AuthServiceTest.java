package com.botica.backend.service;

import com.botica.backend.config.JwtPrincipal;
import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.dto.CredencialesLoginRequest;
import com.botica.backend.dto.LoginResponse;
import com.botica.backend.dto.UsuarioResponse;
import com.botica.backend.exception.CredencialesInvalidasException;
import com.botica.backend.model.Usuario;
import com.botica.backend.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioDao usuarioDao;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_conCredencialesValidas_generaTokenConLosClaimsDeD1() {
        AuthService service = new AuthService(usuarioDao, passwordEncoder, jwtUtil);
        Usuario usuario = usuarioDePrueba();
        when(usuarioDao.buscarPorUsuario("rosa.quispe")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("tecnico123", usuario.getPasswordHash())).thenReturn(true);
        when(jwtUtil.generar(any())).thenReturn("token-firmado");

        LoginResponse respuesta = service.login(new CredencialesLoginRequest("rosa.quispe", "tecnico123", "Tarde"));

        assertThat(respuesta.token()).isEqualTo("token-firmado");
        assertThat(respuesta.usuario().rol()).isEqualTo("TECNICO");
        assertThat(respuesta.usuario().turno()).isEqualTo("Tarde"); // el turno lo manda el LOGIN, no usuarios.turno (decisión 2026-09-08)
        assertThat(respuesta.usuario().boticaNombre()).isEqualTo("Botica San Lucas");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(jwtUtil).generar(captor.capture());
        Map<String, Object> claims = captor.getValue();
        // Por D1 (multi-botica): el JWT lleva boticaId, no solo usuarioId/rol/turno.
        assertThat(claims).containsEntry("usuarioId", 1L).containsEntry("boticaId", 1L).containsEntry("rol", "TECNICO");
    }

    @Test
    void login_conUsuarioInexistente_lanzaCredencialesInvalidas_sinLlamarAlPasswordEncoder() {
        AuthService service = new AuthService(usuarioDao, passwordEncoder, jwtUtil);
        when(usuarioDao.buscarPorUsuario("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new CredencialesLoginRequest("nadie", "loquesea", "Tarde")))
                .isInstanceOf(CredencialesInvalidasException.class);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_conPasswordIncorrecta_lanzaCredencialesInvalidas() {
        AuthService service = new AuthService(usuarioDao, passwordEncoder, jwtUtil);
        Usuario usuario = usuarioDePrueba();
        when(usuarioDao.buscarPorUsuario("rosa.quispe")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mala", usuario.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> service.login(new CredencialesLoginRequest("rosa.quispe", "mala", "Tarde")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void yo_sinAutenticacionEnElContexto_devuelveNull_noLanza401() {
        AuthService service = new AuthService(usuarioDao, passwordEncoder, jwtUtil);

        UsuarioResponse resultado = service.yo();

        assertThat(resultado).isNull();
    }

    @Test
    void yo_conJwtValidoEnElContexto_devuelveElUsuarioReal() {
        AuthService service = new AuthService(usuarioDao, passwordEncoder, jwtUtil);
        JwtPrincipal principal = new JwtPrincipal(1L, 1L, "TECNICO", "Noche", "rosa.quispe");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        when(usuarioDao.buscarPorId(1L)).thenReturn(Optional.of(usuarioDePrueba()));

        UsuarioResponse resultado = service.yo();

        assertThat(resultado).isNotNull();
        assertThat(resultado.turno()).isEqualTo("Noche"); // el turno de /yo sale del JWT (el que se eligió al loguear), no de la tabla usuarios
    }

    private Usuario usuarioDePrueba() {
        return Usuario.builder()
                .id(1L).boticaId(1L).boticaNombre("Botica San Lucas").boticaDireccion("Av. Grau 412")
                .nombre("Rosa Quispe").usuario("rosa.quispe").passwordHash("hash").rol("TECNICO")
                .build();
    }
}
