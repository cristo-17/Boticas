package com.botica.backend.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * id null = presentación nueva. Con id = una que ya existe: se actualizan
 * etiqueta y precio (el precio nuevo solo afecta ventas futuras: venta_detalle
 * lo tiene congelado), y si estaba desactivada vuelve a activarse. Una
 * presentación existente que no viene en la lista se desactiva, nunca se borra.
 */
public record PresentacionRequest(
        Long id,
        @NotBlank(message = "etiqueta es obligatoria") @Size(max = 30, message = "etiqueta admite hasta 30 caracteres") String etiqueta,
        @NotNull(message = "factorConversion es obligatorio") @Positive(message = "factorConversion debe ser mayor a 0") Integer factorConversion,
        @NotNull(message = "precio es obligatorio") @Positive(message = "precio debe ser mayor a 0")
        @Digits(integer = 8, fraction = 2, message = "precio admite 2 decimales") BigDecimal precio
) {
}
