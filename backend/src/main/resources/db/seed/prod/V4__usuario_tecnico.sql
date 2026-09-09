-- ============================================================
-- V4 (perfil prod) — segundo usuario real: un TECNICO, además del
-- ADMINISTRADOR de V2__seed.sql (que no se toca: ya aplicada, cruzó
-- "La frontera", docs/DESPLIEGUE.md).
--
-- Motivo (usuario, 2026-09-09): la persona que usa el sistema a diario
-- no debería entrar como ADMINISTRADOR, y demostrar 403 SIN_PERMISO de
-- un TECNICO contra POST /api/caja/cerrar en la URL pública (no solo
-- en local) es evidencia directa de que el control por roles funciona
-- de verdad desplegado.
--
-- Mismo patrón que V2: valores reales por variable de entorno vía
-- placeholders de Flyway (spring.flyway.placeholders.*, ver
-- application-prod.properties) — ningún dato real vive en este archivo
-- ni en el repo. ${boticaNombre} se reutiliza (Flyway resuelve los
-- placeholders para toda la corrida, no por archivo) — prod tiene una
-- sola botica.
--
-- turno: NOT NULL en el esquema (V1, ya aplicada, no se toca) pero sin
-- ningún consumidor real en el código — verificado con grep exhaustivo
-- sobre backend/src/main/java: ni UsuarioDaoJdbc ni el modelo Usuario
-- lo seleccionan siquiera. El turno que sí importa (caja_diaria.turno,
-- el claim del JWT) sale del login en cada sesión (hueco 7,
-- docs/DECISIONES.md), nunca de esta columna. Mismo hallazgo aplica al
-- ADMIN de V2 (su ADMIN_TURNO tampoco se lee en ningún lado) — ahí no
-- se corrige por estar ya aplicada; acá se resuelve con un literal fijo
-- en vez de una variable de entorno nueva, porque no hay ninguna
-- decisión real que capturar. Deuda aceptada, detalle en
-- docs/DECISIONES.md — candidata a limpiar en Fase 2 (dropear la
-- columna o darle un uso real).
-- ============================================================

INSERT INTO usuarios (botica_id, rol_id, nombre, usuario, password_hash, turno) VALUES
    ((SELECT id FROM boticas WHERE nombre = '${boticaNombre}'),
     (SELECT id FROM roles WHERE nombre = 'TECNICO'),
     '${tecnicoNombre}', '${tecnicoUsuario}', '${tecnicoPasswordHash}', 'Mañana');
