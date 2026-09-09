package com.botica.backend.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El bug clásico (docs/prompts/PROMPT-AGENTE-BACKEND-QA.md, riesgos
 * conocidos): un servidor en UTC cambia de día a las 7pm hora peruana y
 * "la caja de hoy" deja de encontrarse. Este test fija el reloj a las
 * 11pm hora de Lima -- momento en que UTC YA está en el día siguiente --
 * y verifica que FechaNegocio.hoy() siga devolviendo el día de Lima.
 */
class FechaNegocioTest {

    @Test
    void hoy_alas11pmHoraLima_devuelveElDiaDeLima_noElDeUtc() {
        // 2026-09-08 23:00 America/Lima == 2026-09-09 04:00 UTC (Lima es UTC-5 fijo, sin DST)
        Instant instante2300Lima = LocalDate.of(2026, 9, 8)
                .atTime(23, 0)
                .atOffset(ZoneOffset.of("-05:00"))
                .toInstant();

        FechaNegocio fechaNegocio = new FechaNegocio(Clock.fixed(instante2300Lima, FechaNegocio.ZONA_LIMA));

        assertThat(fechaNegocio.hoy()).isEqualTo(LocalDate.of(2026, 9, 8));
        // Si alguien reemplazara la zona por UTC, este mismo instante daría 2026-09-09 --
        // justo el bug que este test existe para atrapar.
        assertThat(LocalDate.ofInstant(instante2300Lima, ZoneOffset.UTC)).isEqualTo(LocalDate.of(2026, 9, 9));
    }

    @Test
    void hoy_pocoDespuesDeMedianocheLima_yaEsElDiaSiguiente() {
        Instant instante0010Lima = LocalDate.of(2026, 9, 9)
                .atTime(0, 10)
                .atOffset(ZoneOffset.of("-05:00"))
                .toInstant();

        FechaNegocio fechaNegocio = new FechaNegocio(Clock.fixed(instante0010Lima, FechaNegocio.ZONA_LIMA));

        assertThat(fechaNegocio.hoy()).isEqualTo(LocalDate.of(2026, 9, 9));
    }
}
