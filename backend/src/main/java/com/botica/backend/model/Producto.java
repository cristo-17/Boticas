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
    private OffsetDateTime creadoEn;
    private Long creadoPor;
}
