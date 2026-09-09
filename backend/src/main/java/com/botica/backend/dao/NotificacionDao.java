package com.botica.backend.dao;

import com.botica.backend.model.Notificacion;

import java.util.List;

public interface NotificacionDao {

    Notificacion insertar(Notificacion n);

    List<Notificacion> listarRecientes(Long boticaId, String rol, int limite);

    int contarNoLeidas(Long boticaId, String rol);

    void marcarComoLeida(Long boticaId, Long id);

    void marcarTodasComoLeidas(Long boticaId, String rol);
}
