package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Espeja la tabla productos. unidadNombre (V3) es el sustantivo real de
 * la unidad base (p.ej. "tableta") — el backend nunca compone la frase
 * de una presentación, solo manda el dato para que el frontend la arme.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    private Long id;
    private Long boticaId;
    private String nombre;
    private String laboratorio;
    private String categoria;
    private String codigoBarras;
    private String unidadNombre;
    /** V9: false = desactivado por el administrador (no se vende ni recibe lotes). */
    @Builder.Default
    private Boolean activo = true;
    /** V10: true = solo se vende con receta (D-E2-4). La API lo exige; el DEFAULT false de la base es temporal (R23). Cada venta lo congela en venta_detalle. */
    private boolean requiereReceta;
    private OffsetDateTime creadoEn;
    private Long creadoPor;
}
