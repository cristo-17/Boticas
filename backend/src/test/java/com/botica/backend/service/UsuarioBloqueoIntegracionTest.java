package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.dto.ActualizarUsuarioRequest;
import com.botica.backend.dto.CrearUsuarioRequest;
import com.botica.backend.dto.CredencialesLoginRequest;
import com.botica.backend.dto.LoginResponse;
import com.botica.backend.dto.RestablecerClaveRequest;
import com.botica.backend.dto.UsuarioAdminResponse;
import com.botica.backend.exception.CredencialesInvalidasException;
import com.botica.backend.exception.CuentaBloqueadaException;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.util.FechaNegocio;
import com.botica.backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 17, Bloque B (DIF-01), contra la base real (botica_test):
 * <ul>
 *   <li>El contador de intentos se incrementa en SQL ({@code intentos_fallidos = intentos_fallidos + 1}).</li>
 *   <li>5 intentos fallidos seguidos bloquean 15 minutos con {@code 423 CUENTA_BLOQUEADA}.</li>
 *   <li>Con reloj fijo: durante los 15 minutos ni la clave correcta entra; al pasar 15 minutos vuelve a entrar y reinicia el contador.</li>
 *   <li>El administrador puede desbloquear con {@code desbloquear} o {@code restablecerClave}.</li>
 *   <li>Un usuario desactivado no inicia sesión y {@code buscarPorId} deja de devolverlo; si tenía caja abierta, {@code tieneCajaAbierta} lo informa.</li>
 * </ul>
 */
@SpringBootTest
@Transactional
class UsuarioBloqueoIntegracionTest {

    private static final Instant T0 = Instant.parse("2026-11-02T15:00:00Z"); // 10:00 en Lima

    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private UsuarioDao usuarioDao;
    @Autowired
    private CajaDao cajaDao;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private ContextoOperacion contexto;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void cincoIntentosFallidos_bloquean15MinutosEnSql_yElTiempoODesbloqueoLoLiberan() {
        String handle = "qa.bloqueo." + (System.nanoTime() % 1_000_000L);
        UsuarioAdminResponse creado = usuarioService.crear(
                new CrearUsuarioRequest("Técnico Bloqueo QA", handle, "claveCorrecta99", "TECNICO"));

        AuthService authEnT0 = new AuthService(usuarioDao, passwordEncoder, jwtUtil,
                new FechaNegocio(Clock.fixed(T0, FechaNegocio.ZONA_LIMA)));

        // Intentos 1 a 4: 401 CREDENCIALES_INVALIDAS y el contador sube de 1 en 1 en SQL.
        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> authEnT0.login(new CredencialesLoginRequest(handle, "claveMala", "Tarde")))
                    .isInstanceOf(CredencialesInvalidasException.class);
            assertThat(intentosEnSql(creado.id())).isEqualTo(i);
        }

        // Intento 5: 423 CUENTA_BLOQUEADA hasta las 10:15 (hora de Lima).
        assertThatThrownBy(() -> authEnT0.login(new CredencialesLoginRequest(handle, "claveMala", "Tarde")))
                .isInstanceOf(CuentaBloqueadaException.class)
                .hasMessage("Cuenta bloqueada hasta las 10:15");
        assertThat(intentosEnSql(creado.id())).isEqualTo(5);

        // A los 14 minutos: ni con la contraseña correcta entra.
        AuthService authEnT14 = new AuthService(usuarioDao, passwordEncoder, jwtUtil,
                new FechaNegocio(Clock.fixed(T0.plusSeconds(14 * 60), FechaNegocio.ZONA_LIMA)));
        assertThatThrownBy(() -> authEnT14.login(new CredencialesLoginRequest(handle, "claveCorrecta99", "Tarde")))
                .isInstanceOf(CuentaBloqueadaException.class);

        // A los 15 minutos exactos: el bloqueo ya venció, entra y reinicia intentos_fallidos a 0.
        AuthService authEnT15 = new AuthService(usuarioDao, passwordEncoder, jwtUtil,
                new FechaNegocio(Clock.fixed(T0.plusSeconds(15 * 60), FechaNegocio.ZONA_LIMA)));
        LoginResponse ok = authEnT15.login(new CredencialesLoginRequest(handle, "claveCorrecta99", "Tarde"));
        assertThat(ok.usuario().usuario()).isEqualTo(handle);
        assertThat(intentosEnSql(creado.id())).isZero();
    }

    @Test
    void desbloquearYRestablecerClave_liberanAlUsuarioBloqueado() {
        String handle = "qa.desbloq." + (System.nanoTime() % 1_000_000L);
        UsuarioAdminResponse creado = usuarioService.crear(
                new CrearUsuarioRequest("Técnico Desbloqueo QA", handle, "claveCorrecta99", "TECNICO"));
        AuthService authEnT0 = new AuthService(usuarioDao, passwordEncoder, jwtUtil,
                new FechaNegocio(Clock.fixed(T0, FechaNegocio.ZONA_LIMA)));

        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> authEnT0.login(new CredencialesLoginRequest(handle, "mala", "Tarde")))
                    .isInstanceOf(RuntimeException.class);
        }

        // 1) Desbloquear desde el administrador permite entrar de inmediato en T0.
        UsuarioAdminResponse desbloqueado = usuarioService.desbloquear(creado.id());
        assertThat(desbloqueado.bloqueado()).isFalse();
        assertThat(desbloqueado.intentosFallidos()).isZero();
        assertThat(authEnT0.login(new CredencialesLoginRequest(handle, "claveCorrecta99", "Tarde")).token()).isNotBlank();

        // Volver a bloquear y liberar mediante restablecer-clave.
        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> authEnT0.login(new CredencialesLoginRequest(handle, "mala", "Tarde")))
                    .isInstanceOf(RuntimeException.class);
        }
        UsuarioAdminResponse ConNuevaClave = usuarioService.restablecerClave(
                creado.id(), new RestablecerClaveRequest("otraClaveNueva88"));
        assertThat(ConNuevaClave.bloqueado()).isFalse();
        assertThat(ConNuevaClave.intentosFallidos()).isZero();
        assertThat(authEnT0.login(new CredencialesLoginRequest(handle, "otraClaveNueva88", "Tarde")).token()).isNotBlank();
    }

    @Test
    void desactivarTecnicoConCajaAbierta_sePermiteInformaCajaYLeImpideIniciarSesion() {
        String handle = "qa.inactivo." + (System.nanoTime() % 1_000_000L);
        UsuarioAdminResponse creado = usuarioService.crear(
                new CrearUsuarioRequest("Técnico Con Caja", handle, "claveCorrecta99", "TECNICO"));

        cajaDao.insertar(CajaDiaria.builder()
                .boticaId(contexto.boticaId()).usuarioId(creado.id())
                .fecha(new FechaNegocio(Clock.fixed(T0, FechaNegocio.ZONA_LIMA)).hoy())
                .turno("Tarde").montoApertura(new BigDecimal("50.00"))
                .horaApertura(new FechaNegocio(Clock.fixed(T0, FechaNegocio.ZONA_LIMA)).ahora())
                .build());

        UsuarioAdminResponse desactivado = usuarioService.actualizar(
                creado.id(), new ActualizarUsuarioRequest("Técnico Con Caja", "TECNICO", false));
        assertThat(desactivado.activo()).isFalse();
        assertThat(desactivado.tieneCajaAbierta()).as("la caja sigue abierta para que la cierre el administrador (D-E2-1)").isTrue();

        AuthService auth = new AuthService(usuarioDao, passwordEncoder, jwtUtil,
                new FechaNegocio(Clock.fixed(T0, FechaNegocio.ZONA_LIMA)));
        assertThatThrownBy(() -> auth.login(new CredencialesLoginRequest(handle, "claveCorrecta99", "Tarde")))
                .isInstanceOf(CredencialesInvalidasException.class);
        assertThat(usuarioDao.buscarPorId(creado.id())).as("buscarPorId no devuelve inactivos: el filtro JWT y /yo lo rechazan").isEmpty();
    }

    private int intentosEnSql(Long usuarioId) {
        Integer valor = jdbcTemplate.queryForObject(
                "SELECT intentos_fallidos FROM usuarios WHERE id = ?", Integer.class, usuarioId);
        return valor == null ? 0 : valor;
    }
}
