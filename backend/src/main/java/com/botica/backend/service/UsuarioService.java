package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.dto.ActualizarUsuarioRequest;
import com.botica.backend.dto.CrearUsuarioRequest;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.RestablecerClaveRequest;
import com.botica.backend.dto.UsuarioAdminResponse;
import com.botica.backend.exception.AutoModificacionAdminException;
import com.botica.backend.exception.UsuarioDuplicadoException;
import com.botica.backend.exception.UsuarioNoEncontradoException;
import com.botica.backend.model.Usuario;
import com.botica.backend.util.FechaNegocio;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Administración de usuarios de la botica (Tarea 17, Bloque B). El rol
 * ADMINISTRADOR lo exige SecurityConfig; aquí viven las reglas de dominio:
 * <ul>
 *   <li>El administrador solo ve y modifica usuarios de su propia botica (D1).</li>
 *   <li>El nombre de usuario (login) es único globalmente (MODELO-DATOS).</li>
 *   <li>La contraseña se hashea en el servidor con BCrypt y nunca se expone.</li>
 *   <li>Un administrador no puede desactivarse a sí mismo ni quitarse el rol:
 *       la botica podría quedar sin administrador.</li>
 *   <li>Desactivar a un técnico con caja abierta se permite: la caja la cierra
 *       el administrador (D-E2-1), y la respuesta indica {@code tieneCajaAbierta}.</li>
 *   <li>Restablecer clave también limpia {@code intentos_fallidos} y {@code bloqueado_hasta}.</li>
 * </ul>
 */
@Service
public class UsuarioService {

    private final UsuarioDao usuarioDao;
    private final PasswordEncoder passwordEncoder;
    private final ContextoOperacion contexto;
    private final FechaNegocio fechaNegocio;

    public UsuarioService(UsuarioDao usuarioDao,
                          PasswordEncoder passwordEncoder,
                          ContextoOperacion contexto,
                          FechaNegocio fechaNegocio) {
        this.usuarioDao = usuarioDao;
        this.passwordEncoder = passwordEncoder;
        this.contexto = contexto;
        this.fechaNegocio = fechaNegocio;
    }

    public PaginaResponse<UsuarioAdminResponse> listar(String buscar, int pagina, int tamano, String orden) {
        PaginaResponse<Usuario> usuarios = usuarioDao.listarPaginado(contexto.boticaId(), buscar, pagina, tamano, orden);
        OffsetDateTime ahora = fechaNegocio.ahora();
        List<UsuarioAdminResponse> contenido = usuarios.contenido().stream()
                .map(u -> UsuarioAdminResponse.desde(u, ahora))
                .toList();
        return new PaginaResponse<>(contenido, usuarios.pagina(), usuarios.tamano(), usuarios.totalElementos(), usuarios.totalPaginas());
    }

    @Transactional
    public UsuarioAdminResponse crear(CrearUsuarioRequest request) {
        Long boticaId = contexto.boticaId();
        String handle = request.usuario().trim();
        Usuario nuevo = Usuario.builder()
                .boticaId(boticaId)
                .nombre(request.nombre().trim())
                .usuario(handle)
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .creadoPor(contexto.usuarioId())
                .build();
        Long id;
        try {
            id = usuarioDao.insertar(nuevo);
        } catch (DuplicateKeyException e) {
            throw new UsuarioDuplicadoException(handle);
        }
        return obtener(boticaId, id);
    }

    @Transactional
    public UsuarioAdminResponse actualizar(Long usuarioId, ActualizarUsuarioRequest request) {
        Long boticaId = contexto.boticaId();
        usuarioDao.obtenerPorIdEnBotica(boticaId, usuarioId)
                .orElseThrow(UsuarioNoEncontradoException::new);

        if (usuarioId.equals(contexto.usuarioId())) {
            if (!Boolean.TRUE.equals(request.activo())) {
                throw new AutoModificacionAdminException("No puedes desactivar tu propio usuario");
            }
            if (!"ADMINISTRADOR".equals(request.rol())) {
                throw new AutoModificacionAdminException("No puedes quitarte el rol de administrador");
            }
        }

        usuarioDao.actualizar(boticaId, usuarioId, request.nombre().trim(), request.rol(), request.activo());
        return obtener(boticaId, usuarioId);
    }

    @Transactional
    public UsuarioAdminResponse restablecerClave(Long usuarioId, RestablecerClaveRequest request) {
        Long boticaId = contexto.boticaId();
        usuarioDao.obtenerPorIdEnBotica(boticaId, usuarioId)
                .orElseThrow(UsuarioNoEncontradoException::new);
        String hash = passwordEncoder.encode(request.password());
        usuarioDao.actualizarPasswordYDesbloquear(boticaId, usuarioId, hash);
        return obtener(boticaId, usuarioId);
    }

    @Transactional
    public UsuarioAdminResponse desbloquear(Long usuarioId) {
        Long boticaId = contexto.boticaId();
        usuarioDao.obtenerPorIdEnBotica(boticaId, usuarioId)
                .orElseThrow(UsuarioNoEncontradoException::new);
        usuarioDao.desbloquear(boticaId, usuarioId);
        return obtener(boticaId, usuarioId);
    }

    private UsuarioAdminResponse obtener(Long boticaId, Long usuarioId) {
        Usuario u = usuarioDao.obtenerPorIdEnBotica(boticaId, usuarioId)
                .orElseThrow(UsuarioNoEncontradoException::new);
        return UsuarioAdminResponse.desde(u, fechaNegocio.ahora());
    }
}
