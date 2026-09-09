package com.botica.backend.util;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Única puerta de entrada a "qué día/hora es" (Regla 5). Ninguna clase
 * llama a LocalDate.now() ni OffsetDateTime.now() directo — todas pasan
 * por acá, envuelto en un Clock inyectable para poder simular una hora
 * exacta en los tests (p. ej. las 11pm hora de Lima, el caso clásico
 * donde un servidor en UTC ya cambió de día).
 */
@Component
public class FechaNegocio {

    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private final Clock clock;

    public FechaNegocio() {
        this(Clock.system(ZONA_LIMA));
    }

    public FechaNegocio(Clock clock) {
        this.clock = clock;
    }

    public LocalDate hoy() {
        return LocalDate.now(clock);
    }

    public OffsetDateTime ahora() {
        return OffsetDateTime.now(clock);
    }
}
