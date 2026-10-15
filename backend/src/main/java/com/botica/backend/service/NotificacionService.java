package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.NotificacionDao;
import com.botica.backend.dto.NotificacionResponse;
import com.botica.backend.exception.NotificacionNoEncontradaException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * T15-11 (Regla 2): el controller ya no llama al DAO directo. Botica y
 * rol salen SIEMPRE del contexto (el token), nunca del cliente.
 */
@Service
public class NotificacionService {

    /** Tope fijo, sin paginar: es la bandeja de la campana, no un listado de consulta. */
    static final int LIMITE_RECIENTES = 30;

    private final NotificacionDao notificacionDao;
    private final ContextoOperacion contexto;

    public NotificacionService(NotificacionDao notificacionDao, ContextoOperacion contexto) {
        this.notificacionDao = notificacionDao;
        this.contexto = contexto;
    }

    public List<NotificacionResponse> listarRecientes() {
        return notificacionDao.listarRecientes(contexto.boticaId(), contexto.rol(), LIMITE_RECIENTES).stream()
                .map(NotificacionResponse::desde)
                .toList();
    }

    public void marcarComoLeida(Long id) {
        if (notificacionDao.marcarComoLeida(contexto.boticaId(), contexto.rol(), id) == 0) {
            throw new NotificacionNoEncontradaException();
        }
    }

    public void marcarTodasComoLeidas() {
        notificacionDao.marcarTodasComoLeidas(contexto.boticaId(), contexto.rol());
    }
}
