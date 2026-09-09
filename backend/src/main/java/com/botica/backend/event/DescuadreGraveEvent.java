package com.botica.backend.event;

import java.math.BigDecimal;

public record DescuadreGraveEvent(
        Long boticaId,
        Long cajaId,
        String usuarioCajero,
        BigDecimal diferencia,
        BigDecimal esperado,
        BigDecimal contado
) {}
