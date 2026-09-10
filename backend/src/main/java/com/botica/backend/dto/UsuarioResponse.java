package com.botica.backend.dto;

/**
 * rol viaja crudo ("TECNICO"|"ADMINISTRADOR"), no como frase ya armada
 * ("Técnica farmacéutica" del mock viejo, Tarea 6) — decisión 2026-09-09,
 * docs/DECISIONES.md: el frontend ya necesita el código crudo para
 * decidir qué mostrar/ocultar por rol (Tarea 12), y el mismo principio
 * de "el backend nunca manda la frase ya armada" ya se aplicó en
 * Alertas/Inventario. Igual boticaNombre/boticaDireccion, separados en
 * vez del "sede" compuesto del mock — el cliente arma el texto.
 */
public record UsuarioResponse(
        Long id,
        String nombre,
        String usuario,
        String rol,
        String turno,
        String boticaNombre,
        String boticaDireccion
) {
}
