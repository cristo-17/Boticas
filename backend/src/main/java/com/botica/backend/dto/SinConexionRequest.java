package com.botica.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * Tarea 22 (D-E2-7): presente solo en una venta que el cajero cobró sin
 * conexión y que llega desde la cola del dispositivo. Con este bloque la
 * venta ya ocurrió en el mostrador: el servidor la registra siempre (si el
 * cuerpo está bien formado) y cada problema queda en revisiones_venta.
 * cajaId: la caja abierta en la que se vendió (tiene que ser de quien envía).
 * horaDispositivo: solo auditoría; la hora de la venta es la del servidor.
 */
public record SinConexionRequest(
        @NotNull(message = "sinConexion.cajaId es obligatorio") Long cajaId,
        @NotNull(message = "sinConexion.horaDispositivo es obligatoria") OffsetDateTime horaDispositivo
) {
}
