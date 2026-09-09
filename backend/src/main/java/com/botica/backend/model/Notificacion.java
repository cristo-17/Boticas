package com.botica.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacion {

    private Long id;
    private Long boticaId;
    private Long usuarioId;
    private String rolDestinatario;
    private String tipo;
    private String titulo;
    private String mensaje;
    private boolean leido;
    private OffsetDateTime fechaCreacion;
}
