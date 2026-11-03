package com.botica.backend.dto;

import com.botica.backend.model.Receta;

import java.time.LocalDate;

/** Receta guardada con la venta (Tarea 20). En VentaResponse es null si ninguna línea la necesitó. */
public record RecetaResponse(
        String numero,
        String cmp,
        String medicoNombre,
        LocalDate fechaEmision
) {
    public static RecetaResponse desde(Receta receta) {
        return new RecetaResponse(receta.getNumero(), receta.getCmp(), receta.getMedicoNombre(), receta.getFechaEmision());
    }
}
