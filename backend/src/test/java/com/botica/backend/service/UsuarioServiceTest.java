package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.dto.ActualizarUsuarioRequest;
import com.botica.backend.dto.CrearUsuarioRequest;
import com.botica.backend.dto.RestablecerClaveRequest;
import com.botica.backend.dto.UsuarioAdminResponse;
import com.botica.backend.exception.AutoModificacionAdminException;
import com.botica.backend.exception.UsuarioDuplicadoException;
import com.botica.backend.exception.UsuarioNoEncontradoException;
import com.botica.backend.model.Usuario;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas de dominio de administración de usuarios (Tarea 17, Bloque B), sin base de datos. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceTest {

    private static final Long BOTICA_ID = 1L;
    private static final Long ADMIN_ID = 2L;
    private static final Long TECNICO_ID = 5L;
    private static final Instant INSTANTE_FIJO = Instant.parse("2026-11-02T15:00:00Z");

    @Mock
    private UsuarioDao usuarioDao;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ContextoOperacion contexto;

    private final FechaNegocio fechaNegocio = new FechaNegocio(Clock.fixed(INSTANTE_FIJO, FechaNegocio.ZONA_LIMA));
    private UsuarioService service;

    @BeforeEach
    void configurar() {
        service = new UsuarioService(usuarioDao, passwordEncoder, contexto, fechaNegocio);
        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(ADMIN_ID);
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hashDePrueba");
    }

    @Test
    void crear_hasheaLaPassword_yAsignaLaBoticaYElAutorDelContexto() {
        when(usuarioDao.insertar(any())).thenReturn(TECNICO_ID);
        when(usuarioDao.obtenerPorIdEnBotica(BOTICA_ID, TECNICO_ID)).thenReturn(Optional.of(usuario(TECNICO_ID, "TECNICO", true)));

        service.crear(new CrearUsuarioRequest("  Lucía Gómez ", " lucia.gomez ", "claveSegura123", "TECNICO"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioDao).insertar(captor.capture());
        assertThat(captor.getValue().getBoticaId()).isEqualTo(BOTICA_ID);
        assertThat(captor.getValue().getCreadoPor()).isEqualTo(ADMIN_ID);
        assertThat(captor.getValue().getNombre()).isEqualTo("Lucía Gómez");
        assertThat(captor.getValue().getUsuario()).isEqualTo("lucia.gomez");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashDePrueba");
    }

    @Test
    void crear_conUsuarioRepetido_devuelveUsuarioDuplicado() {
        when(usuarioDao.insertar(any())).thenThrow(new DuplicateKeyException("uk"));

        assertThatThrownBy(() -> service.crear(new CrearUsuarioRequest("Lucía Gómez", "rosa.quispe", "claveSegura123", "TECNICO")))
                .isInstanceOf(UsuarioDuplicadoException.class);
    }

    @Test
    void actualizar_adminNoPuedeDesactivarseASiMismo() {
        when(usuarioDao.obtenerPorIdEnBotica(BOTICA_ID, ADMIN_ID)).thenReturn(Optional.of(usuario(ADMIN_ID, "ADMINISTRADOR", true)));

        assertThatThrownBy(() -> service.actualizar(ADMIN_ID, new ActualizarUsuarioRequest("Admin", "ADMINISTRADOR", false)))
                .isInstanceOf(AutoModificacionAdminException.class)
                .hasMessageContaining("desactivar tu propio usuario");
        verify(usuarioDao, never()).actualizar(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void actualizar_adminNoPuedeQuitarseElRolASiMismo() {
        when(usuarioDao.obtenerPorIdEnBotica(BOTICA_ID, ADMIN_ID)).thenReturn(Optional.of(usuario(ADMIN_ID, "ADMINISTRADOR", true)));

        assertThatThrownBy(() -> service.actualizar(ADMIN_ID, new ActualizarUsuarioRequest("Admin", "TECNICO", true)))
                .isInstanceOf(AutoModificacionAdminException.class)
                .hasMessageContaining("quitarte el rol");
        verify(usuarioDao, never()).actualizar(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void actualizar_adminSiPuedeCambiarSuPropioNombre() {
        when(usuarioDao.obtenerPorIdEnBotica(BOTICA_ID, ADMIN_ID)).thenReturn(Optional.of(usuario(ADMIN_ID, "ADMINISTRADOR", true)));

        service.actualizar(ADMIN_ID, new ActualizarUsuarioRequest("Carlos M.", "ADMINISTRADOR", true));

        verify(usuarioDao).actualizar(BOTICA_ID, ADMIN_ID, "Carlos M.", "ADMINISTRADOR", true);
    }

    @Test
    void actualizar_desactivarTecnicoConCajaAbierta_sePermite() {
        Usuario tecnicoConCaja = usuario(TECNICO_ID, "TECNICO", true);
        tecnicoConCaja.setTieneCajaAbierta(true);
        when(usuarioDao.obtenerPorIdEnBotica(BOTICA_ID, TECNICO_ID)).thenReturn(Optional.of(tecnicoConCaja));

        UsuarioAdminResponse respuesta = service.actualizar(TECNICO_ID, new ActualizarUsuarioRequest("Rosa Quispe", "TECNICO", false));

        verify(usuarioDao).actualizar(BOTICA_ID, TECNICO_ID, "Rosa Quispe", "TECNICO", false);
        assertThat(respuesta.tieneCajaAbierta()).isTrue();
    }

    @Test
    void actualizar_usuarioDeOtraBoticaOInexistente_devuelve404() {
        when(usuarioDao.obtenerPorIdEnBotica(BOTICA_ID, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(999L, new ActualizarUsuarioRequest("Otro", "TECNICO", true)))
                .isInstanceOf(UsuarioNoEncontradoException.class);
    }

    @Test
    void restablecerClave_hasheaYDesbloquea() {
        when(usuarioDao.obtenerPorIdEnBotica(BOTICA_ID, TECNICO_ID)).thenReturn(Optional.of(usuario(TECNICO_ID, "TECNICO", true)));

        service.restablecerClave(TECNICO_ID, new RestablecerClaveRequest("nuevaClave89"));

        verify(usuarioDao).actualizarPasswordYDesbloquear(BOTICA_ID, TECNICO_ID, "$2a$10$hashDePrueba");
    }

    @Test
    void desbloquear_limpiaIntentosYBloqueo() {
        when(usuarioDao.obtenerPorIdEnBotica(BOTICA_ID, TECNICO_ID)).thenReturn(Optional.of(usuario(TECNICO_ID, "TECNICO", true)));

        service.desbloquear(TECNICO_ID);

        verify(usuarioDao).desbloquear(BOTICA_ID, TECNICO_ID);
    }

    private static Usuario usuario(Long id, String rol, boolean activo) {
        return Usuario.builder()
                .id(id).boticaId(BOTICA_ID).nombre("Rosa Quispe").usuario("rosa.quispe")
                .rol(rol).activo(activo).intentosFallidos(0).tieneCajaAbierta(false)
                .build();
    }
}
