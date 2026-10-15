package com.botica.backend.service;

import com.botica.backend.dto.NotificacionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Conexiones SSE abiertas, en memoria (una sola instancia del backend).
 *
 * El único job programado del proyecto es {@link #heartbeat()} (H7, R18):
 * manda un comentario cada 45 s para que proxies no corten un stream
 * inactivo y para descartar clientes muertos. NO detecta ni calcula nada
 * de negocio: stock crítico, vencidos y cajas sin cerrar salen de
 * GET /api/alertas y /api/dashboard/resumen al consultar, y cada
 * notificación queda guardada en la tabla aunque nadie esté conectado.
 *
 * Si el job no corre 8 horas (Render gratis dormido): los streams ya se
 * cayeron con el contenedor, el navegador reconecta al despertar, y la
 * campana recupera lo pendiente con GET /api/notificaciones. Lo único que
 * se pierde es el aviso "en el momento"; ningún dato. Con el contenedor
 * despierto y el job parado, un cliente muerto queda en la lista hasta que
 * el siguiente despachar() falle al escribirle y lo quite.
 */
@Service
public class SseEmitterService {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterService.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutos

    private record ClienteSse(Long boticaId, Long usuarioId, String rol, SseEmitter emitter) {}

    private final List<ClienteSse> clientes = new CopyOnWriteArrayList<>();

    public SseEmitter crearConexion(Long boticaId, Long usuarioId, String rol) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        ClienteSse cliente = new ClienteSse(boticaId, usuarioId, rol, emitter);
        clientes.add(cliente);

        Runnable remover = () -> clientes.remove(cliente);
        emitter.onCompletion(remover);
        emitter.onTimeout(remover);
        emitter.onError(e -> remover.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECT")
                    .data("Conexión establecida con el servicio de alertas nativas"));
        } catch (IOException e) {
            remover.run();
        }

        return emitter;
    }

    public void despachar(Long boticaId, String rolDestinatario, NotificacionResponse notificacion) {
        for (ClienteSse c : clientes) {
            if (c.boticaId().equals(boticaId)) {
                if (rolDestinatario == null || rolDestinatario.equalsIgnoreCase(c.rol())) {
                    try {
                        c.emitter().send(SseEmitter.event()
                                .name("ALERTA")
                                .data(notificacion));
                    } catch (IOException e) {
                        log.debug("Error enviando alerta SSE a usuario {}, removiendo conexión", c.usuarioId());
                        clientes.remove(c);
                    }
                }
            }
        }
    }

    @Scheduled(fixedRate = 45000)
    public void heartbeat() {
        for (ClienteSse c : clientes) {
            try {
                c.emitter().send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                clientes.remove(c);
            }
        }
    }
}
