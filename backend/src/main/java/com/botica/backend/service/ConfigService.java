package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.dto.ConfigResponse;
import com.botica.backend.dto.VencimientoConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConfigService {

    private static final List<String> MOTIVOS_MERMA =
            List.of("Vencimiento", "Rotura", "Deterioro", "Robo o pérdida", "Otro");
    private static final List<String> MOTIVOS_QUE_REQUIEREN_OBSERVACION =
            List.of("Robo o pérdida", "Otro");
    public static final List<String> MOTIVOS_EGRESO =
            List.of("Pago a proveedor", "Servicios básicos", "Insumos y limpieza", "Movilidad y encomiendas", "Otro");

    private final ConfigNegocioProperties propiedades;
    private final String version;

    public ConfigService(ConfigNegocioProperties propiedades, @Value("${app.version}") String version) {
        this.propiedades = propiedades;
        this.version = version;
    }

    public ConfigResponse obtener() {
        return new ConfigResponse(
                propiedades.getIgv(),
                propiedades.getUmbralStockBajo(),
                propiedades.getDescuadreLeve(),
                MOTIVOS_MERMA,
                MOTIVOS_QUE_REQUIEREN_OBSERVACION,
                MOTIVOS_EGRESO,
                new VencimientoConfig(propiedades.getVencimientoCriticoDias(), propiedades.getVencimientoAdvertenciaDias()),
                version
        );
    }
}
