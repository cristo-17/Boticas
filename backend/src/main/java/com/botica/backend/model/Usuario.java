package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Espeja usuarios JOIN roles JOIN boticas — nunca se expone tal cual
 * (passwordHash no sale de acá); ver dto/UsuarioResponse.
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
}
