package com.botica.backend.controller;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.config.JwtPrincipal;
import com.botica.backend.dto.NotificacionResponse;
import com.botica.backend.dto.TicketSseResponse;
import com.botica.backend.service.NotificacionService;
import com.botica.backend.service.SseEmitterService;
import com.botica.backend.service.SseTicketService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;
    private final SseEmitterService sseEmitterService;
    private final SseTicketService sseTicketService;
    private final ContextoOperacion contexto;

    public NotificacionController(NotificacionService notificacionService, SseEmitterService sseEmitterService,
                                  SseTicketService sseTicketService, ContextoOperacion contexto) {
        this.notificacionService = notificacionService;
        this.sseEmitterService = sseEmitterService;
        this.sseTicketService = sseTicketService;
        this.contexto = contexto;
    }

    // Autenticado por cabecera como todo lo demás. El ticket es lo único que viaja en la URL de /stream (T15-02).
    @PostMapping("/ticket")
    public ResponseEntity<TicketSseResponse> pedirTicket(@AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(new TicketSseResponse(sseTicketService.emitir(principal), SseTicketService.VIGENCIA_SEGUNDOS));
    }

    // ?ticket= de POST /ticket -- lo canjea JwtAuthenticationFilter, nunca un JWT.
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter conectarStream() {
        return sseEmitterService.crearConexion(contexto.boticaId(), contexto.usuarioId(), contexto.rol());
    }

    @GetMapping
    public ResponseEntity<List<NotificacionResponse>> listar() {
        return ResponseEntity.ok(notificacionService.listarRecientes());
    }

    @PutMapping("/{id}/leer")
    public ResponseEntity<Void> marcarComoLeida(@PathVariable Long id) {
        notificacionService.marcarComoLeida(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/leer-todas")
    public ResponseEntity<Void> marcarTodasComoLeidas() {
        notificacionService.marcarTodasComoLeidas();
        return ResponseEntity.noContent().build();
    }
}
