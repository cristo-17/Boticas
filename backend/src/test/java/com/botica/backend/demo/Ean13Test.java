package com.botica.backend.demo;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 19: el dígito verificador que usa la V12 para generar los EAN-13 demo es el mismo que el
 * frontend usa para descartar lecturas de la cámara y del lector USB. Los dos lados leen
 * test-vectores/ean.json (ean.util.spec.ts del lado del frontend).
 */
class Ean13Test {

    @Test
    void vectoresCompartidos_testVectoresEanJson_elServidorDaLoMismoQueElFrontend() throws Exception {
        JsonNode vectores = new ObjectMapper().readTree(Path.of("../test-vectores/ean.json").toFile()).get("vectores");
        assertThat(vectores.size()).isGreaterThan(0);

        for (JsonNode v : vectores) {
            String codigo = v.get("codigo").asString();
            assertThat(Ean13.esValido(codigo))
                    .as("EAN-13 '%s' (%s)", codigo, v.get("nota").asString())
                    .isEqualTo(v.get("esEan13").asBoolean());
        }
    }

    @Test
    void esValido_null_esFalso() {
        assertThat(Ean13.esValido(null)).isFalse();
    }
}
