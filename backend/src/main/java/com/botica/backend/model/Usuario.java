package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {
    private Long id;
    private Long boticaId;
    private Long rolId;
    private String rolNombre;
    private String nombre;
    private String usuario;
    private String passwordHash;
    private String turno;
    private Boolean activo;
    private String boticaNombre;
    private String boticaDireccion;
}
