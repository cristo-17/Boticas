package com.botica.backend.dao;

import com.botica.backend.model.Notificacion;

import java.util.List;

public interface NotificacionDao {

    Notificacion insertar(Notificacion n);

    List<Notificacion> listarRecientes(Long boticaId, String rol, int limite);

    int contarNoLeidas(Long boticaId, String rol);

    /** Devuelve las filas tocadas: 0 = no existe, es de otra botica o no es para ese rol. */
    int marcarComoLeida(Long boticaId, String rol, Long id);

    void marcarTodasComoLeidas(Long boticaId, String rol);
}
