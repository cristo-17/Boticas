package com.botica.backend.exception;

import com.botica.backend.model.CajaDiaria;
import org.springframework.http.HttpStatus;

import java.time.format.DateTimeFormatter;

/**
 * Una sola caja abierta por usuario, de cualquier día o turno (DECISIONES
 * 2026-09-08; DIF-12, Tarea 0B). El mensaje dice cuál cerrar primero.
 */
public class CajaYaAbiertaException extends NegocioException {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public CajaYaAbiertaException(CajaDiaria abierta) {
        super(HttpStatus.CONFLICT, "CAJA_YA_ABIERTA",
                "Tienes abierta la caja del " + abierta.getFecha().format(FORMATO_FECHA)
                        + ", turno " + abierta.getTurno() + ". Ciérrala antes de abrir otra.");
    }

    /** Carrera: dos aperturas a la vez y el índice único rechazó la segunda; la transacción ya no admite otra consulta. */
    public CajaYaAbiertaException() {
        super(HttpStatus.CONFLICT, "CAJA_YA_ABIERTA", "Ya tienes una caja abierta. Ciérrala antes de abrir otra.");
    }
}
