package com.botica.backend.dto;

import com.botica.backend.model.CajaDiaria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Forma exacta de docs/API-CONTRATO.md. montoEsperado/diferencia/
 * semaforoDescuadre quedan null mientras la caja sigue ABIERTA — conteo
 * ciego (hueco 2): el servidor no los calcula ni los expone hasta el
 * cierre. estado se traduce al booleano "abierta" acá, no en la BD.
 */
public record CajaResponse(
        Long id,
        LocalDate fecha,
        Long usuarioId,
        String usuarioNombre,
        String turno,
        BigDecimal montoApertura,
        OffsetDateTime horaApertura,
        OffsetDateTime horaCierre,
        BigDecimal montoContado,
        BigDecimal montoEsperado,
        BigDecimal diferencia,
        String semaforoDescuadre,
        String observaciones,
        boolean abierta
) {
    public static CajaResponse desde(CajaDiaria c) {
        return new CajaResponse(
                c.getId(), c.getFecha(), c.getUsuarioId(), c.getUsuarioNombre(), c.getTurno(),
                c.getMontoApertura(), c.getHoraApertura(), c.getHoraCierre(), c.getMontoContado(),
                c.getMontoEsperado(), c.getDiferencia(), c.getSemaforoDescuadre(), c.getObservaciones(),
                c.estaAbierta()
        );
    }
}
