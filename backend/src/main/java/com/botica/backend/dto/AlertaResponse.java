package com.botica.backend.dto;

import java.util.Map;

/**
 * Forma sin cambios respecto al mock del frontend (docs/API-CONTRATO.md):
 * a diferencia de ResumenDashboard, acá el mensaje SÍ viaja ya armado —
 * titulo/cuerpo son oraciones, no números que el cliente vaya a
 * formatear. id es sintético (no hay una tabla "alertas"): compone el
 * tipo de alerta con el id de la fila que la origina, para que sea
 * estable entre una página y la siguiente.
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
