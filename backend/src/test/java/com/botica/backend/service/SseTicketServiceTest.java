package com.botica.backend.service;

import com.botica.backend.config.JwtPrincipal;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SseTicketServiceTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 10, 14, 10, 0, 0, 0, ZoneOffset.ofHours(-5));
    private static final JwtPrincipal TECNICO = new JwtPrincipal(1L, 1L, "TECNICO", "Tarde", "tecnico");

    private final FechaNegocio fechaNegocio = mock(FechaNegocio.class);
    private final SseTicketService service = new SseTicketService(fechaNegocio);

    @Test
    void ticket_canjeadoDentroDeLos60Segundos_devuelveElUsuario() {
        when(fechaNegocio.ahora()).thenReturn(T0, T0.plusSeconds(59));
        String ticket = service.emitir(TECNICO);

        assertThat(service.canjear(ticket)).contains(TECNICO);
    }

    @Test
    void ticket_canjeadoAlSegundo60_yaVencio() {
        when(fechaNegocio.ahora()).thenReturn(T0, T0.plusSeconds(60));
        String ticket = service.emitir(TECNICO);

        assertThat(service.canjear(ticket)).isEmpty();
    }

    @Test
    void ticketInventado_noAutentica() {
        when(fechaNegocio.ahora()).thenReturn(T0);

        assertThat(service.canjear("no-existe")).isEmpty();
    }
}
