package com.botica.backend.service;

import com.botica.backend.config.JwtPrincipal;
import com.botica.backend.util.FechaNegocio;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T15-02 (H2): EventSource no puede mandar la cabecera Authorization, y
 * el JWT en la URL queda en logs de acceso y proxies con 12 h de validez.
 * En su lugar viaja un ticket opaco: un solo uso, 60 s de vida, y SOLO
 * lo acepta {@link com.botica.backend.config.JwtAuthenticationFilter}
 * en /api/notificaciones/stream.
 * En memoria, igual que {@link SseEmitterService}: vale con una sola
 * instancia del backend (Render hoy). Los vencidos se purgan al emitir,
 * sin @Scheduled (R18).
 */
@Service
public class SseTicketService {

    public static final int VIGENCIA_SEGUNDOS = 60;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private record Ticket(JwtPrincipal principal, OffsetDateTime expira) {
    }

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final FechaNegocio fechaNegocio;

    public SseTicketService(FechaNegocio fechaNegocio) {
        this.fechaNegocio = fechaNegocio;
    }

    public String emitir(JwtPrincipal principal) {
        OffsetDateTime ahora = fechaNegocio.ahora();
        tickets.values().removeIf(t -> !t.expira().isAfter(ahora));

        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Ticket(principal, ahora.plusSeconds(VIGENCIA_SEGUNDOS)));
        return ticket;
    }

    /** Un solo uso: el ticket se borra al canjearlo, sea válido o esté vencido. */
    public Optional<JwtPrincipal> canjear(String ticket) {
        Ticket t = tickets.remove(ticket);
        if (t == null || !t.expira().isAfter(fechaNegocio.ahora())) {
            return Optional.empty();
        }
        return Optional.of(t.principal());
    }
}
