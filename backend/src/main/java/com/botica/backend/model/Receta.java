package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Espeja la tabla recetas (V10, D-E2-4): una por venta, solo si alguna línea requiere receta. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Receta {

    private Long id;
    private Long boticaId;
    private Long ventaId;
    private String numero;
    private String cmp;
    private String medicoNombre;
    private LocalDate fechaEmision;
    private Long creadoPor;
}
