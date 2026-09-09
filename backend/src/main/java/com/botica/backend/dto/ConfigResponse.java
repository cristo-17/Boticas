package com.botica.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Forma exacta de docs/API-CONTRATO.md, sección "Configuración de
 * negocio". Reemplaza las constantes que hoy están hardcodeadas y
 * duplicadas en el frontend (TASA_IGV, UMBRAL_STOCK_BAJO, etc.).
 */
public record ConfigResponse(
        BigDecimal igv,
        int umbralStockBajo,
        BigDecimal descuadreLeve,
        List<String> motivosMerma,
        List<String> motivosQueRequierenObservacion,
        VencimientoConfig vencimiento
) {
}
