package com.botica.backend.dto;

import java.time.LocalDate;

/** Tarea 22: fecha y stock de un lote vendible, sin id ni costo: con esto el dispositivo calcula su stock aproximado del día. */
public record LoteVendibleResponse(LocalDate fechaVencimiento, int stock) {
}
