-- ============================================================
-- V6 — corrige V5 (notificaciones) sin editarla (T16, hallazgo H6)
-- ============================================================
-- V5 ya corrió en Neon (2026-09-09): una migración aplicada no se toca,
-- Flyway rechazaría el checksum. Esta V6 agrega lo que le faltó según
-- R16: COMMENT ON TABLE, ON DELETE explícito en cada FK y CHECK en las
-- columnas tipo enum. (El encabezado de V5 dice "V4": queda así.)
--
-- Corre sobre una tabla CON filas. Ninguna sentencia agrega NOT NULL ni
-- reescribe datos; los CHECK validan las filas existentes y, si alguna no
-- cumple, la migración entera se revierte (DDL transaccional en Postgres)
-- en vez de dejar la tabla a medias.
-- Fuera de alcance (pendiente de decisión del usuario): fecha_creacion ->
-- creado_en y la columna creado_por (regla 9).

-- FKs: mismo criterio que el resto del esquema (V1): botica_id y usuario_id
-- son RESTRICT. Los nombres son los que Postgres genera por defecto para el
-- REFERENCES de V5; sin IF EXISTS a propósito: si el nombre no coincide, la
-- migración falla en vez de dejar una FK duplicada.
ALTER TABLE notificaciones DROP CONSTRAINT notificaciones_botica_id_fkey;
ALTER TABLE notificaciones
    ADD CONSTRAINT notificaciones_botica_id_fkey
    FOREIGN KEY (botica_id) REFERENCES boticas(id) ON DELETE RESTRICT;

ALTER TABLE notificaciones DROP CONSTRAINT notificaciones_usuario_id_fkey;
ALTER TABLE notificaciones
    ADD CONSTRAINT notificaciones_usuario_id_fkey
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE RESTRICT;

-- CHECKs: los únicos valores que el código escribió desde V5
-- (NotificacionEventListener).
ALTER TABLE notificaciones
    ADD CONSTRAINT notificaciones_tipo_check
    CHECK (tipo IN ('STOCK_CRITICO', 'DESCUADRE_GRAVE'));

ALTER TABLE notificaciones
    ADD CONSTRAINT notificaciones_rol_destinatario_check
    CHECK (rol_destinatario IS NULL OR rol_destinatario IN ('TECNICO', 'ADMINISTRADOR'));

COMMENT ON TABLE notificaciones IS 'Avisos en vivo (SSE) de STOCK_CRITICO y DESCUADRE_GRAVE. rol_destinatario NULL = toda la botica; con valor, solo ese rol los ve y los marca. leido es compartido por botica/rol, no por usuario. usuario_id nunca se llena hoy. fecha_creacion hace de creado_en (no renombrada, ver V6). FKs RESTRICT, como el resto del esquema.';
