package com.botica.backend.dto;

import com.botica.backend.model.Usuario;

import java.time.OffsetDateTime;

/**
 * Usuario tal como lo ve y edita el administrador en /api/usuarios (Tarea 17, Bloque B).
 * NUNCA incluye passwordHash. Incluye estado de bloqueo (DIF-01) y si tiene
 * una caja ABIERTA para avisar al desactivarlo (D-E2-1).
 */
public record UsuarioAdminResponse(
        Long id,
        String nombre,
        String usuario,
        String rol,
        boolean activo,
        int intentosFallidos,
        OffsetDateTime bloqueadoHasta,
        boolean bloqueado,
        boolean tieneCajaAbierta,
        OffsetDateTime creadoEn
) {
    public static UsuarioAdminResponse desde(Usuario usuario, OffsetDateTime ahora) {
        OffsetDateTime hasta = usuario.getBloqueadoHasta();
        boolean bloqueado = hasta != null && hasta.isAfter(ahora);
        return new UsuarioAdminResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getUsuario(),
                usuario.getRol(),
                Boolean.TRUE.equals(usuario.getActivo()),
                usuario.getIntentosFallidos() == null ? 0 : usuario.getIntentosFallidos(),
                bloqueado ? hasta : null,
                bloqueado,
                Boolean.TRUE.equals(usuario.getTieneCajaAbierta()),
                usuario.getCreadoEn()
        );
    }
}
