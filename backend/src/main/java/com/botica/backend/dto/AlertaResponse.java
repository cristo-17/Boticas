package com.botica.backend.dto;

import java.util.Map;

public record AlertaResponse(
        String id,
        String nivel,
        String titulo,
        String cuerpo,
        String monto,
        String accionLabel,
        String accionRuta,
        Map<String, String> accionQueryParams
) {}
