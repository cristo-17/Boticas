-- ============================================================
-- V10 — venta con receta y Kardex (Tarea 20, D-E2-4 y D-E2-5)
-- ============================================================
-- Corre sobre tablas CON filas (productos, lotes, ventas y movimientos de
-- la botica real y, en local, del seed). Qué cambia:
--   1. productos.requiere_receta (NOT NULL DEFAULT false): las filas
--      existentes quedan en false. La API exige el campo (400 si falta); el
--      DEFAULT se queda en V10 solo por compatibilidad con el código de V9
--      (R23, ver abajo) y se quita en la próxima migración, cuando R25
--      confirme V10 en producción.
--   1b. venta_detalle.requiere_receta (NOT NULL DEFAULT false): la venta
--      congela por línea el valor del producto en ese momento, como el
--      precio y el costo (RF-19). El Kardex clasifica con esta columna:
--      cambiar el catálogo no reescribe lo ya dispensado. Las líneas
--      existentes quedan en false, que es lo que valía todo producto en ese
--      momento (la columna de productos nace en esta misma migración).
--   2. Tabla recetas (D-E2-4).
--   3. Entradas faltantes en movimientos_stock: hasta V9, registrar un lote
--      no escribía su INGRESO, así que el Kardex de los lotes existentes no
--      cuadraba con su stock. Se completa una entrada por lote.
--   4. movimientos_stock pasa a ser inmutable (trigger).
--   5. DROP de la tabla kardex, vacía desde V1 (D-E2-5).
--
-- Compatibilidad hacia atrás (R23): si el deploy nuevo no arranca, la
-- versión anterior sigue sirviendo con este esquema. Lo que se rompe en ese
-- caso, y solo mientras dure:
--   - Nada en productos ni ventas: el INSERT viejo de productos no manda
--     requiere_receta y el de venta_detalle tampoco; los dos DEFAULT false
--     lo cubren (el producto nace sin receta; la línea, sin receta).
--   - Un lote registrado por el código viejo no escribe su INGRESO y no
--     cuadra en el Kardex hasta que se corrija con otra migración.
-- La versión anterior no lee ni escribe kardex (grep: sin uso en Java) ni
-- hace UPDATE/DELETE sobre movimientos_stock.
-- Probada en botica_prod_sim antes del deploy (R23): camino de producción
-- hasta V9, con lotes, ventas y mermas creados antes de la migración.

-- --- 1. productos.requiere_receta --------------------------------------

-- DEFAULT false solo por R23: si el deploy falla después de migrar, el código
-- de V9 (que no manda la columna) sigue pudiendo crear productos. La API nueva
-- exige el campo igual. PENDIENTE: DROP DEFAULT en la próxima migración,
-- después de que R25 confirme V10 en producción.
ALTER TABLE productos ADD COLUMN requiere_receta BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN productos.requiere_receta IS 'true = solo se vende con receta médica: una venta con al menos una línea de este producto exige los datos de la receta (400 RECETA_REQUERIDA). Afecta solo ventas futuras: cada línea congela el valor en venta_detalle.requiere_receta. El DEFAULT false es temporal (compatibilidad con el código de V9, R23); la API exige el campo.';

-- --- 1b. venta_detalle.requiere_receta (congelado por línea) --------------

ALTER TABLE venta_detalle ADD COLUMN requiere_receta BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN venta_detalle.requiere_receta IS 'Valor de productos.requiere_receta al momento de la venta, CONGELADO como precio_unitario y costo_unitario (RF-19): desmarcar o marcar el producto después no cambia lo ya dispensado. El Kardex clasifica SALIDA_RECETA / SALIDA_LIBRE con esta columna. Las líneas anteriores a V10 quedaron en false (ningún producto requería receta). El DEFAULT cubre las ventas que registre el código de V9 (R23).';

-- --- 2. recetas ---------------------------------------------------------

CREATE TABLE recetas (
    id             BIGSERIAL PRIMARY KEY,
    botica_id      BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    venta_id       BIGINT NOT NULL REFERENCES ventas(id) ON DELETE RESTRICT,
    numero         VARCHAR(30) NOT NULL CHECK (btrim(numero) <> ''),
    cmp            VARCHAR(6) NOT NULL CHECK (cmp ~ '^[0-9]{1,6}$'),
    medico_nombre  VARCHAR(150),
    fecha_emision  DATE,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por     BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    CONSTRAINT recetas_venta_unica UNIQUE (venta_id)
);
COMMENT ON TABLE recetas IS 'Receta médica de una venta (D-E2-4): número de receta y CMP del médico obligatorios; nombre del médico y fecha de emisión opcionales. Una por venta. Solo se guarda si la venta tiene al menos una línea de un producto con requiere_receta. venta_id es RESTRICT: la receta es el respaldo de la dispensación y no se pierde en cascada. fecha_emision es DATE de negocio (Lima).';

CREATE INDEX idx_recetas_botica_venta ON recetas(botica_id, venta_id);

-- --- 3. Entradas faltantes de los lotes existentes -----------------------

-- Una ENTRADA (tipo INGRESO) por lote, por stock − SUM(movimientos): con eso
-- el saldo del Kardex del lote es exactamente su stock. Fecha: la menor entre
-- creado_en del lote y su primer movimiento; si el primer movimiento no es
-- posterior al lote, 1 ms antes de ese movimiento, para que la entrada quede
-- siempre antes que sus salidas al ordenar por (fecha, id).
-- creado_por y origen_captura en NULL: ninguna persona la registró, y una
-- columna de auditoría no se rellena por convención. El Kardex la rotula
-- "Entrada inicial (registrada por la migración)".
-- Si stock − SUM(movimientos) da 0 o negativo no se inserta nada: se avisa
-- con un NOTICE y se revisa a mano (no debería pasar).
DO $$
DECLARE
    v_sin_entrada INTEGER;
BEGIN
    SELECT count(*) INTO v_sin_entrada
    FROM lotes l
    LEFT JOIN (SELECT lote_id, SUM(cantidad) AS suma FROM movimientos_stock GROUP BY lote_id) m ON m.lote_id = l.id
    WHERE l.stock - COALESCE(m.suma, 0) <= 0;
    IF v_sin_entrada > 0 THEN
        RAISE NOTICE 'V10: % lote(s) sin entrada inicial (stock - SUM(movimientos) <= 0)', v_sin_entrada;
    END IF;
END $$;

INSERT INTO movimientos_stock (botica_id, lote_id, tipo, cantidad, origen_captura, creado_en, creado_por)
SELECT l.botica_id,
       l.id,
       'INGRESO',
       l.stock - COALESCE(m.suma, 0),
       NULL,
       CASE WHEN m.primero IS NULL OR l.creado_en < m.primero THEN l.creado_en
            ELSE m.primero - INTERVAL '1 millisecond' END,
       NULL
FROM lotes l
LEFT JOIN (SELECT lote_id, SUM(cantidad) AS suma, MIN(creado_en) AS primero
           FROM movimientos_stock GROUP BY lote_id) m ON m.lote_id = l.id
WHERE l.stock - COALESCE(m.suma, 0) > 0
ORDER BY l.id;

-- --- 4. movimientos_stock inmutable ---------------------------------------

-- Es el libro del Kardex: una fila nunca cambia ni se borra. Una corrección
-- es un movimiento nuevo. El trigger también bloquea el ON DELETE SET NULL de
-- referencia_venta_id, referencia_merma_id y creado_por: borrar una venta,
-- una merma o un usuario con movimientos falla. Está bien así: en este
-- sistema nada se borra (se desactiva).
CREATE FUNCTION movimientos_stock_inmutable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'movimientos_stock es inmutable: % no permitido', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_movimientos_stock_inmutable
    BEFORE UPDATE OR DELETE ON movimientos_stock
    FOR EACH ROW EXECUTE FUNCTION movimientos_stock_inmutable();

CREATE TRIGGER trg_movimientos_stock_sin_truncate
    BEFORE TRUNCATE ON movimientos_stock
    FOR EACH STATEMENT EXECUTE FUNCTION movimientos_stock_inmutable();

COMMENT ON TABLE movimientos_stock IS 'Ledger de stock y fuente del Kardex (D-E2-5): cantidad negativa = salida (VENTA, MERMA), positiva = ingreso (INGRESO, uno por lote al registrarlo). INMUTABLE desde V10: un trigger rechaza UPDATE, DELETE y TRUNCATE, incluido el SET NULL de sus FK (borrar una venta, merma o usuario con movimientos falla). origen_captura es NULL en mermas e ingresos. creado_por NULL solo en las entradas iniciales que completó V10. botica_id es RESTRICT.';

-- Kardex por producto: filtra por lote y ordena por fecha.
CREATE INDEX idx_movimientos_stock_lote_fecha ON movimientos_stock(lote_id, creado_en, id);

-- --- 5. kardex (D-E2-5) -------------------------------------------------

-- Vacía desde V1 y sin uso en el código: el Kardex se calcula desde
-- movimientos_stock al consultar.
DROP TABLE kardex;
