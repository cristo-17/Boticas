package com.botica.backend.config;

/**
 * Implementación de desarrollo: valores fijos de V2__seed.sql (Rosa
 * Quispe / Botica San Lucas / turno Tarde). id=1 para ambos porque son
 * las primeras filas que inserta el seed con BIGSERIAL — si el orden de
 * inserción de boticas/usuarios en V2__seed.sql cambia alguna vez, estos
 * literales hay que revisarlos.
 *
 * La Tarea 12 REEMPLAZA esta clase por una que lee usuarioId/boticaId/
 * turno del JWT — no crea la interfaz {@link ContextoOperacion}, ya
 * existe desde aquí, y ningún Service cambia una línea al reemplazarla.
 */
// @Component -- Reemplazada por ContextoOperacionJwt (Tarea 12)
public class ContextoOperacionDev implements ContextoOperacion {

    private static final Long USUARIO_ID = 1L;
    private static final Long BOTICA_ID = 1L;
    private static final String TURNO = "Tarde";

    @Override
    public Long usuarioId() {
        return USUARIO_ID;
    }

    @Override
    public Long boticaId() {
        return BOTICA_ID;
    }

    @Override
    public String turno() {
        return TURNO;
    }

    @Override
    public String rol() {
        return "ADMINISTRADOR";
    }
}
