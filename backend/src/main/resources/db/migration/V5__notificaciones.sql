-- ============================================================
-- V4 — notificaciones para alertas en tiempo real y persistencia
-- ============================================================

CREATE TABLE notificaciones (
    id BIGSERIAL PRIMARY KEY,
    botica_id BIGINT NOT NULL REFERENCES boticas(id),
    usuario_id BIGINT REFERENCES usuarios(id),
    rol_destinatario VARCHAR(30),
    tipo VARCHAR(30) NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    mensaje TEXT NOT NULL,
    leido BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notificaciones_botica_leido 
    ON notificaciones(botica_id, leido, fecha_creacion DESC);
