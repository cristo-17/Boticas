package com.botica.backend.config;

/**
 * Identidad de la operación en curso: quién la hace, en qué botica, en
 * qué turno. Ningún Service sabe de dónde salen estos tres valores —
 * hoy vienen de {@link ContextoOperacionDev} con datos fijos del seed;
 * la Tarea 12 los reemplaza por una implementación que lee el JWT, sin
 * tocar un solo Service que dependa de esta interfaz.
 *
 * Multi-botica (D1, docs/DECISIONES.md) y las columnas de auditoría
 * necesitan esta identidad desde ya, aunque la autenticación real
 * todavía no exista.
 */
public interface ContextoOperacion {

    Long usuarioId();

    Long boticaId();

    String turno();

    /** Sin valor por defecto (T15-10): un contexto que no lo defina no puede heredar el rol de mayor privilegio. */
    String rol();
}

