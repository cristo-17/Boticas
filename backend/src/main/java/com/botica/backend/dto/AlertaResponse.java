package com.botica.backend.dto;

import java.util.Map;

/**
 * Forma de docs/API-CONTRATO.md:
 * titulo/cuerpo son oraciones en español ya formateadas por el Service.
 * id es sintético ("venc-<loteId>", "stock-critico", "caja-<cajaId>").
 */
public record AlertaResponse(
        String id,
        String nivel,
        String titulo,
        String cuerpo,
        String monto,
        String accionLabel,
        String accionRuta,
        Map<String, String> accionQueryParams
) {
}
