package com.botica.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Los umbrales numéricos de negocio (GET /api/config) viven en
 * application.properties, no hardcodeados en Java — así que ajustar el
 * umbral de stock bajo o la tasa de IGV no pide recompilar. Los dos
 * catálogos fijos (motivos de merma) sí quedan como constantes Java en
 * ConfigService: cambiarlos requiere coordinar con el frontend de todos
 * modos (Regla 11), así que una propiedad no ahorra nada ahí y sí
 * arriesga problemas de encoding con las tildes en un .properties.
 */
@Component
@ConfigurationProperties(prefix = "app.config-negocio")
@Getter
@Setter
public class ConfigNegocioProperties {

    private BigDecimal igv = new BigDecimal("0.18");
    private int umbralStockBajo = 15;
    private BigDecimal descuadreLeve = new BigDecimal("10.00");
    private int vencimientoCriticoDias = 30;
    private int vencimientoAdvertenciaDias = 90;
}
