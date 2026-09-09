package com.botica.backend.dto;

import java.util.List;

/**
 * Envoltura de paginación uniforme (Anexo D) para todo endpoint que
 * devuelva una colección que pueda crecer. Genérica y reutilizable —
 * ningún endpoint paginado inventa su propia forma de respuesta.
 */
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas
) {
    public static <T> PaginaResponse<T> de(List<T> contenido, int pagina, int tamano, long totalElementos) {
        int totalPaginas = tamano <= 0 ? 0 : (int) Math.ceil((double) totalElementos / tamano);
        return new PaginaResponse<>(contenido, pagina, tamano, totalElementos, totalPaginas);
    }
}
