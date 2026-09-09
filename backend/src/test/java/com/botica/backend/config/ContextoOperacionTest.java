package com.botica.backend.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Reemplaza a ContextoOperacionJwt SOLO bajo el perfil "test" (activado
 * por maven-surefire-plugin, pom.xml) — para los tests de integración
 * que llaman Services/DAOs directo, sin pasar por HTTP
 * (VentaConcurrenciaTest, VentaIdempotenciaConcurrenteTest y
 * similares): ahí no hay una petición real, así que no hay JWT que
 * leer del SecurityContext. Vive en src/test/java, nunca se empaqueta
 * en el jar de producción. Mismos valores fijos que la vieja
 * ContextoOperacionDev (retirada en la Tarea 12): primera botica/
 * usuario del seed.
 */
@Component
@Profile("test")
public class ContextoOperacionTest implements ContextoOperacion {

    private static final Long USUARIO_ID = 1L;
    private static final Long BOTICA_ID = 1L;
    private static final String TURNO = "Tarde";

    @Override
    public Long usuarioId() {
        return USUARIO_ID;
    }

    @Override
    public Long boticaId() {
        return BOTICA_ID;
    }

    @Override
    public String turno() {
        return TURNO;
    }
}
