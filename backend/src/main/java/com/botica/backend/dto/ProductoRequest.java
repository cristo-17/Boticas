package com.botica.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Alta (POST) y edición (PUT) de un producto con sus presentaciones, en
 * la misma petición y la misma transacción (Tarea 17). Las reglas que no
 * son de formato (exactamente una presentación de factor 1, etiquetas sin
 * repetir, factor no editable) las decide el Service.
 */
public record ProductoRequest(
        @NotBlank(message = "nombre es obligatorio") @Size(max = 200, message = "nombre admite hasta 200 caracteres") String nombre,
        @Size(max = 120, message = "laboratorio admite hasta 120 caracteres") String laboratorio,
        @NotBlank(message = "categoria es obligatoria") @Size(max = 80, message = "categoria admite hasta 80 caracteres") String categoria,
        @NotBlank(message = "codigoBarras es obligatorio") @Size(max = 20, message = "codigoBarras admite hasta 20 caracteres") String codigoBarras,
        @NotBlank(message = "unidadNombre es obligatorio") @Size(max = 30, message = "unidadNombre admite hasta 30 caracteres") String unidadNombre,
        /** Tarea 20: obligatorio en la API (400 si falta). La base tiene DEFAULT false temporal solo para el código de V9 (R23). */
        @NotNull(message = "requiereReceta es obligatorio") Boolean requiereReceta,
        @NotEmpty(message = "presentaciones no puede estar vacío") List<@Valid PresentacionRequest> presentaciones
) {
}
