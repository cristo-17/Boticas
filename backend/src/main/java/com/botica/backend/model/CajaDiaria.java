package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Espeja la tabla caja_diaria. estado ("ABIERTA"/"CERRADA") es la
 * representación en BD; el contrato JSON la traduce al booleano
 * "abierta" en CajaResponse — la traducción vive ahí, no aquí.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CajaDiaria {

    public static final String ABIERTA = "ABIERTA";
    public static final String CERRADA = "CERRADA";

    private Long id;
    private Long boticaId;
    private Long usuarioId;
    private String usuarioNombre;
    private LocalDate fecha;
    private String turno;
    private BigDecimal montoApertura;
    private OffsetDateTime horaApertura;
    private OffsetDateTime horaCierre;
    private BigDecimal montoContado;
    private BigDecimal montoEsperado;
    private BigDecimal diferencia;
    private String semaforoDescuadre;
    private String observaciones;
    private String estado;

    public boolean estaAbierta() {
        return ABIERTA.equals(estado);
    }
}
