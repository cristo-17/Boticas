package com.botica.backend.dto;

import java.time.LocalDate;

/**
 * GET /api/kardex (Tarea 20, solo ADMINISTRADOR). desde/hasta son días de
 * Lima, ambos opcionales. Con "desde", la primera fila de la primera página
 * es SALDO_ANTERIOR y el saldo acumulado sigue desde ahí.
 */
public record KardexResponse(
        Long productoId,
        String productoNombre,
        String unidadNombre,
        boolean requiereReceta,
        boolean activo,
        LocalDate desde,
        LocalDate hasta,
        ResumenKardexResponse resumen,
        PaginaResponse<FilaKardexResponse> movimientos
) {
}
