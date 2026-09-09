-- ============================================================
-- V3 — productos.unidad_nombre (primera migración después de la
-- frontera de Flyway cruzada en producción el 2026-09-08 21:03:34;
-- docs/DESPLIEGUE.md). Nunca se edita V1/V2, esto es una migración
-- nueva.
--
-- Sin DEFAULT a propósito (misma regla que origen_captura, D4): si en
-- el futuro hay un formulario de alta de producto, que esté obligado a
-- preguntar la unidad en vez de heredar un valor inventado. Sin
-- riesgo de romper producción: productos tiene 0 filas ahí (el seed de
-- prod no crea ningún producto de muestra) y el ALTER corre limpio
-- contra una tabla vacía. El backfill de los 30 productos de
-- desarrollo va en el seed de dev (db/seed/dev/), no acá.
-- ============================================================

ALTER TABLE productos ADD COLUMN unidad_nombre VARCHAR(30) NOT NULL;

COMMENT ON COLUMN productos.unidad_nombre IS 'Sustantivo de la unidad base del producto (p.ej. "tableta", "cápsula", "sobre", "frasco") — describe factor_conversion de cada presentación (p.ej. "100 " + unidad_nombre) sin inventar un sustantivo genérico. El frontend compone el texto, el backend nunca manda una frase ya armada (docs/API-CONTRATO.md).';
