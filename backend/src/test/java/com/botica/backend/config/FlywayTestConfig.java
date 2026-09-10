package com.botica.backend.config;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Solo existe en el classpath de test (src/test/java) — nunca se
 * empaqueta en el jar de producción, así que no hay forma de que esto
 * corra contra Neon ni contra botica_db por accidente. Reemplaza el
 * migrate-al-arrancar normal de Spring por clean()+migrate(): cada vez
 * que un @SpringBootTest levanta el contexto contra botica_test
 * (apuntada por spring.datasource.url en el <systemPropertyVariables>
 * de maven-surefire-plugin, pom.xml), la base se reconstruye desde
 * cero antes de que corra cualquier test — dos corridas seguidas de
 * "./mvnw test" dan el mismo resultado, sin depender de qué dejó la
 * corrida anterior ([BE-005], docs/BITACORA.md).
 * Se detecta por component scan normal (mismo paquete base que
 * BackendApplication) — no hace falta @Import en cada clase de test.
 */
@Configuration
public class FlywayTestConfig {

    @Bean
    public FlywayMigrationStrategy limpiarYMigrarAntesDeCadaSuite() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
