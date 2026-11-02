package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Espeja usuarios JOIN roles JOIN boticas — nunca se expone tal cual
 * (passwordHash no sale de acá); ver dto/UsuarioResponse y dto/UsuarioAdminResponse.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    private Long id;
    private Long boticaId;
    private String boticaNombre;
    private String boticaDireccion;
    private String nombre;
    private String usuario;
    private String passwordHash;
    private String rol;
    @Builder.Default
    private Boolean activo = true;
    @Builder.Default
    private Integer intentosFallidos = 0;
    private OffsetDateTime bloqueadoHasta;
    private OffsetDateTime creadoEn;
    private Long creadoPor;
    private Boolean tieneCajaAbierta;
}
