-- ============================================================
-- V7 — notificaciones cumple la regla 9 (T16, decisión del usuario 2026-10-14)
-- ============================================================
-- Corre sobre una tabla CON filas y se despliega junto con V6.
-- RENAME COLUMN no reescribe datos; el índice idx_notificaciones_botica_leido
-- y el NOT NULL/DEFAULT de la columna la siguen solos. creado_por entra NULL
-- en todas las filas existentes: hoy todas las genera el sistema. El JSON de
-- la API no cambia (NotificacionResponse sigue exponiendo "fechaCreacion").

ALTER TABLE notificaciones RENAME COLUMN fecha_creacion TO creado_en;

-- Mismo patrón que creado_por en V1 (productos, presentaciones).
ALTER TABLE notificaciones
    ADD COLUMN creado_por BIGINT REFERENCES usuarios(id) ON DELETE SET NULL;

COMMENT ON COLUMN notificaciones.creado_por IS 'NULL = generada por el sistema.';

COMMENT ON TABLE notificaciones IS 'Avisos en vivo (SSE) de STOCK_CRITICO y DESCUADRE_GRAVE. rol_destinatario NULL = toda la botica; con valor, solo ese rol los ve y los marca. leido es compartido por botica/rol, no por usuario. usuario_id nunca se llena hoy. creado_por NULL = generada por el sistema (V7). FKs botica_id/usuario_id RESTRICT, como el resto del esquema.';
