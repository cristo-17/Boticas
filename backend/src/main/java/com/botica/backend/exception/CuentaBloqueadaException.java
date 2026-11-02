package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * DIF-01 (Tarea 17): 5 intentos fallidos seguidos bloquean al usuario
 * durante 15 minutos. El mensaje incluye la hora de Lima (HH:mm) hasta
 * la que dura el bloqueo.
 */
public class CuentaBloqueadaException extends NegocioException {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private final OffsetDateTime bloqueadoHasta;

    public CuentaBloqueadaException(OffsetDateTime bloqueadoHasta) {
        super(HttpStatus.LOCKED, "CUENTA_BLOQUEADA",
                "Cuenta bloqueada hasta las " + bloqueadoHasta.atZoneSameInstant(ZONA_LIMA).format(FORMATO_HORA));
        this.bloqueadoHasta = bloqueadoHasta;
    }

    public OffsetDateTime getBloqueadoHasta() {
        return bloqueadoHasta;
    }
}
