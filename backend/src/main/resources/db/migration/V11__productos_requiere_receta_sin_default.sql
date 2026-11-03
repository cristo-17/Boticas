-- ============================================================
-- V11 — productos.requiere_receta sin DEFAULT (cierre de la Tarea 20)
-- ============================================================
-- V10 dejó DEFAULT false en productos.requiere_receta solo por R23: si el
-- deploy de V10 fallaba después de migrar, el código de V9 (que no manda la
-- columna) tenía que poder seguir creando productos. R25 ya confirmó V10 en
-- producción (f02c09c), así que el DEFAULT temporal se quita: cada INSERT
-- de productos tiene que declarar si el producto requiere receta, igual que
-- ya lo exige la API (400 sin requiereReceta).
--
-- No cambia ninguna fila: DROP DEFAULT solo afecta los INSERT futuros, y la
-- columna sigue NOT NULL. Es un cambio de catálogo, sin reescribir la tabla.
--
-- venta_detalle.requiere_receta CONSERVA su DEFAULT false (decisión de V10):
-- no se toca aquí.
--
-- Compatibilidad hacia atrás (R23): si el deploy nuevo no arranca, sirve el
-- código de V10, cuyo único INSERT INTO productos (ProductoDaoJdbc) siempre
-- manda requiere_receta. Un rollback a V10 sobre V11 no rompe nada.
-- Probada en botica_prod_sim antes del deploy (R23): camino de producción
-- hasta V10 con datos cargados por la API del jar de V10, y el jar de V10
-- arrancado sobre V11 creando producto, lote y venta.

ALTER TABLE productos ALTER COLUMN requiere_receta DROP DEFAULT;

COMMENT ON COLUMN productos.requiere_receta IS 'true = solo se vende con receta médica: una venta con al menos una línea de este producto exige los datos de la receta (400 RECETA_REQUERIDA). Afecta solo ventas futuras: cada línea congela el valor en venta_detalle.requiere_receta. Sin DEFAULT desde V11: todo INSERT lo declara, como la API.';
