package com.botica.backend.controller;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.NotificacionDao;
import com.botica.backend.dto.NotificacionResponse;
import com.botica.backend.model.Notificacion;
import com.botica.backend.service.SseEmitterService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final NotificacionDao notificacionDao;
    private final SseEmitterService sseEmitterService;
    private final ContextoOperacion contexto;

    public NotificacionController(NotificacionDao notificacionDao, SseEmitterService sseEmitterService, ContextoOperacion contexto) {
        this.notificacionDao = notificacionDao;
        this.sseEmitterService = sseEmitterService;
        this.contexto = contexto;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter conectarStream() {
        return sseEmitterService.crearConexion(contexto.boticaId(), contexto.usuarioId(), contexto.rol());
    }

    @GetMapping
    public ResponseEntity<List<NotificacionResponse>> listar() {
        List<Notificacion> lista = notificacionDao.listarRecientes(contexto.boticaId(), contexto.rol(), 30);
        List<NotificacionResponse> respuesta = lista.stream()
                .map(n -> new NotificacionResponse(
                        n.getId(),
                        n.getTipo(),
                        n.getTitulo(),
                        n.getMensaje(),
                        n.isLeido(),
                        n.getFechaCreacion()
                ))
                .toList();
        return ResponseEntity.ok(respuesta);
    }

    @PutMapping("/{id}/leer")
    public ResponseEntity<Void> marcarComoLeida(@PathVariable Long id) {
        notificacionDao.marcarComoLeida(contexto.boticaId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/leer-todas")
    public ResponseEntity<Void> marcarTodasComoLeidas() {
        notificacionDao.marcarTodasComoLeidas(contexto.boticaId(), contexto.rol());
        return ResponseEntity.noContent().build();
    }
}
