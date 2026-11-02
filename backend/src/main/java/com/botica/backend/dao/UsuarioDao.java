package com.botica.backend.dao;

import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.model.Usuario;

import java.time.OffsetDateTime;
import java.util.Optional;

/** No sabe qué es HTTP (Regla 2). Solo usuarios activos (activo = true) en login y filtro de sesión; el listado y la edición de administración incluyen inactivos. */
public interface UsuarioDao {

    Optional<Usuario> buscarPorUsuario(String usuario);

    Optional<Usuario> buscarPorId(Long id);

    /**
     * Incrementa intentos_fallidos en SQL (intentos_fallidos = intentos_fallidos + 1)
     * y, si este intento llega a {@code maxIntentos} o más, fija bloqueado_hasta = :bloqueadoHasta.
     * Si la cuenta tenía un bloqueo ya vencido (bloqueado_hasta <= :ahora), empieza el nuevo ciclo desde 1.
     */
    Optional<Usuario> registrarIntentoFallido(Long usuarioId, OffsetDateTime ahora, int maxIntentos, OffsetDateTime bloqueadoHasta);

    void reiniciarIntentosYBloqueo(Long usuarioId);

    PaginaResponse<Usuario> listarPaginado(Long boticaId, String buscar, int pagina, int tamano, String orden);

    Optional<Usuario> obtenerPorIdEnBotica(Long boticaId, Long usuarioId);

    Long insertar(Usuario usuario);

    void actualizar(Long boticaId, Long usuarioId, String nombre, String rol, boolean activo);

    void actualizarPasswordYDesbloquear(Long boticaId, Long usuarioId, String passwordHash);

    void desbloquear(Long boticaId, Long usuarioId);
}
