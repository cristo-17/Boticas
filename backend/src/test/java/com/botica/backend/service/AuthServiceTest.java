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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // 2026-11-02 10:00:00 en Lima (-05:00) = 15:00:00Z
    private static final Instant INSTANTE_FIJO = Instant.parse("2026-11-02T15:00:00Z");

    @Mock
    private UsuarioDao usuarioDao;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;

    private final FechaNegocio fechaNegocio = new FechaNegocio(Clock.fixed(INSTANTE_FIJO, FechaNegocio.ZONA_LIMA));

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_conCredencialesValidas_generaTokenConLosClaimsDeD1() {
        AuthService service = new AuthService(usuarioDao, passwordEncoder, jwtUtil, fechaNegocio);
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
        AuthService service = new AuthService(usuarioDao, passwordEncoder, jwtUtil, fechaNegocio);
        when(usuarioDao.buscarPorUsuario("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new CredencialesLoginRequest("nadie", "loquesea", "Tarde")))
                .isInstanceOf(CredencialesInvalidasException.class);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_conPasswordIncorrecta_incrementaIntentosFallidos_yLanzaCredencialesInvalidas() {
        AuthService service = new AuthService(usuarioDao, passwordEncoder, jwtUtil, fechaNegocio);
        Usuario usuario = usuarioDePrueba();
        when(usuarioDao.buscarPorUsuario("rosa.quispe")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mala", usuario.getPasswordHash())).thenReturn(false);
        when(usuarioDao.registrarIntentoFallido(eq(1L), any(), eq(5), any())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.login(new CredencialesLoginRequest("rosa.quispe", "mala", "Tarde")))
                .isInstanceOf(CredencialesInvalidasException.class);
        verify(usuarioDao).registrarIntentoFallido(eq(1L), eq(fechaNegocio.ahora()), eq(5), eq(fechaNegocio.ahora().plusMinutes(15)));
    }

    @Test
    void login_enElQuintoIntentoFallido_bloquea15Minutos_yLanzaCuentaBloqueadaConHoraDeLima() {
        AuthService service = new AuthService(usuarioDao, passwordEncoder, jwtUtil, fechaNegocio);
        Usuario usuario = usuarioDePrueba();
        OffsetDateTime hasta = fechaNegocio.ahora().plusMinutes(15); // 10:15 en Lima
        Usuario bloqueado = usuarioDePrueba();
        bloqueado.setIntentosFallidos(5);
        bloqueado.setBloqueadoHasta(hasta);

        when(usuarioDao.buscarPorUsuario("rosa.quispe")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mala", usuario.getPasswordHash())).thenReturn(false);
        when(usuarioDao.registrarIntentoFallido(1L, fechaNegocio.ahora(), 5, hasta)).thenReturn(Optional.of(bloqueado));

        assertThatThrownBy(() -> service.login(new CredencialesLoginRequest("rosa.quispe", "mala", "Tarde")))
                .isInstanceOf(CuentaBloqueadaException.class)
                .hasMessage("Cuenta bloqueada hasta las 10:15");
    }

    @Test
    void login_conCuentaBloqueadaVigente_rechazaConCuentaBloqueada_inclusoConPasswordCorrecta() {
        AuthService service = new AuthService(usuarioDao, passwordEncoder, jwtUtil, fechaNegocio);
        Usuario usuario = usuarioDePrueba();
        usuario.setIntentosFallidos(5);
        usuario.setBloqueadoHasta(fechaNegocio.ahora().plusMinutes(10)); // 10:10 en Lima
        when(usuarioDao.buscarPorUsuario("rosa.quispe")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.login(new CredencialesLoginRequest("rosa.quispe", "tecnico123", "Tarde")))
                .isInstanceOf(CuentaBloqueadaException.class)
                .hasMessage("Cuenta bloqueada hasta las 10:10");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_correctoConIntentosPreviosOBloqueoVencido_reiniciaElContador() {
        AuthService service = new AuthService(usuarioDao, passwordEncoder, jwtUtil, fechaNegocio);
        Usuario usuario = usuarioDePrueba();
        usuario.setIntentosFallidos(3);
        usuario.setBloqueadoHasta(fechaNegocio.ahora().minusMinutes(1)); // ya venció
        when(usuarioDao.buscarPorUsuario("rosa.quispe")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("tecnico123", usuario.getPasswordHash())).thenReturn(true);
        when(jwtUtil.generar(any())).thenReturn("token-firmado");

        service.login(new CredencialesLoginRequest("rosa.quispe", "tecnico123", "Tarde"));

        verify(usuarioDao).reiniciarIntentosYBloqueo(1L);
    }

    @Test
    void yo_sinAutenticacionEnElContexto_devuelveNull_noLanza401() {
        AuthService service = new AuthService(usuarioDao, passwordEncoder, jwtUtil, fechaNegocio);

        UsuarioResponse resultado = service.yo();

        assertThat(resultado).isNull();
    }

    @Test
    void yo_conJwtValidoEnElContexto_devuelveElUsuarioReal() {
        AuthService service = new AuthService(usuarioDao, passwordEncoder, jwtUtil, fechaNegocio);
        JwtPrincipal principal = new JwtPrincipal(1L, 1L, "TECNICO", "Noche", "rosa.quispe");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        when(usuarioDao.buscarPorId(1L)).thenReturn(Optional.of(usuarioDePrueba()));

        UsuarioResponse resultado = service.yo();

        assertThat(resultado).isNotNull();
        assertThat(resultado.turno()).isEqualTo("Noche"); // el turno de /yo sale del JWT (el que se eligió al loguear), no de la tabla usuarios
    }

    @Test
    void yo_cuandoElRolDelTokenNoCoincideConElDeLaBase_devuelveNull() {
        AuthService service = new AuthService(usuarioDao, passwordEncoder, jwtUtil, fechaNegocio);
        JwtPrincipal principal = new JwtPrincipal(1L, 1L, "ADMINISTRADOR", "Tarde", "rosa.quispe");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        when(usuarioDao.buscarPorId(1L)).thenReturn(Optional.of(usuarioDePrueba())); // en BD es TECNICO

        assertThat(service.yo()).isNull();
    }

    private Usuario usuarioDePrueba() {
        return Usuario.builder()
                .id(1L).boticaId(1L).boticaNombre("Botica San Lucas").boticaDireccion("Av. Grau 412")
                .nombre("Rosa Quispe").usuario("rosa.quispe").passwordHash("hash").rol("TECNICO")
                .build();
    }
}
