package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.dto.ConfigResponse;
import com.botica.backend.dto.VencimientoConfig;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConfigService {

    private static final List<String> MOTIVOS_MERMA =
            List.of("Vencimiento", "Rotura", "Deterioro", "Robo o pérdida", "Otro");
    private static final List<String> MOTIVOS_QUE_REQUIEREN_OBSERVACION =
            List.of("Robo o pérdida", "Otro");

    private final ConfigNegocioProperties propiedades;

    public ConfigService(ConfigNegocioProperties propiedades) {
        this.propiedades = propiedades;
    }

    public ConfigResponse obtener() {
        return new ConfigResponse(
                propiedades.getIgv(),
                propiedades.getUmbralStockBajo(),
                propiedades.getDescuadreLeve(),
                MOTIVOS_MERMA,
                MOTIVOS_QUE_REQUIEREN_OBSERVACION,
                new VencimientoConfig(propiedades.getVencimientoCriticoDias(), propiedades.getVencimientoAdvertenciaDias())
        );
    }
}
