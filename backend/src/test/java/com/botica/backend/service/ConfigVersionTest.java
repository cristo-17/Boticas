package com.botica.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R25: GET /api/config dice qué commit está sirviendo el backend. Render
 * define RENDER_GIT_COMMIT en cada deploy; fuera de Render (aquí, la suite)
 * no existe y la versión es "local". Con esto, después de un push se sabe
 * desde fuera si el deploy nuevo arrancó o Render sigue con el anterior.
 */
@SpringBootTest
class ConfigVersionTest {

    @Autowired
    private ConfigService configService;

    @Test
    void sinRenderGitCommit_laVersionEsLocal() {
        assertThat(System.getenv("RENDER_GIT_COMMIT")).as("la suite no corre en Render").isNull();
        assertThat(configService.obtener().version()).isEqualTo("local");
    }

    @Test
    void conRenderGitCommit_laVersionEsEseCommit() {
        ConfigService enRender = new ConfigService(new com.botica.backend.config.ConfigNegocioProperties(), "8f2c1ab");
        assertThat(enRender.obtener().version()).isEqualTo("8f2c1ab");
    }
}
