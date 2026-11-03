package com.botica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Datos de la receta de una venta (D-E2-4, Tarea 20). Número de receta y
 * CMP del médico obligatorios; nombre del médico y fecha de emisión
 * opcionales. Que la venta la necesite (alguna línea con requiereReceta) y
 * que la fecha no sea futura lo decide el Service.
 */
public record RecetaRequest(
        @NotBlank(message = "receta.numero es obligatorio")
        @Size(max = 30, message = "receta.numero admite hasta 30 caracteres") String numero,
        @NotBlank(message = "receta.cmp es obligatorio")
        @Pattern(regexp = "\\s*[0-9]{1,6}\\s*", message = "receta.cmp debe tener de 1 a 6 dígitos") String cmp,
        @Size(max = 150, message = "receta.medicoNombre admite hasta 150 caracteres") String medicoNombre,
        LocalDate fechaEmision
) {
}
