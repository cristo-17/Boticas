-- ============================================================
-- V13 — venta sin conexión (Tarea 22, D-E2-7; docs/DISENO-OFFLINE.md, versión recortada)
-- ============================================================
-- Corre sobre tablas CON filas (la botica real y las tres boticas demo de
-- V12: unas 8 000 ventas). Qué cambia:
--   1. ventas.vendida_sin_conexion (NOT NULL DEFAULT false): las filas
--      existentes son ventas en línea, así que false es su valor verdadero.
--   2. ventas.hora_dispositivo (NULL): la hora del reloj del dispositivo al
--      cobrar sin conexión, solo para auditoría. ventas.fecha sigue siendo la
--      hora del servidor al registrar (también para estas ventas): el reporte
--      y el Kardex usan esa. NULL en toda venta en línea: el servidor no la
--      necesita y no se inventa (CHECK).
--   3. venta_detalle.lote_id y costo_unitario aceptan NULL, los dos juntos:
--      es la línea de las unidades que una venta sin conexión entregó en el
--      mostrador sin stock vendible en el sistema (conflicto 5a). Sin lote no
--      hay costo conocido, y no se inventa ninguno.
--   4. Tabla revisiones_venta: cada problema que el servidor encontró al
--      registrar una venta sin conexión. Una venta "requiere revisión" si
--      tiene alguna PENDIENTE (EXISTS, no una columna aparte).
--
-- No cambia ninguna fila existente: solo agrega columnas con su DEFAULT
-- verdadero y relaja dos NOT NULL.
--
-- Compatibilidad hacia atrás (R23): si el deploy nuevo no arranca, el código
-- de V12 sigue sirviendo sobre este esquema:
--   - su INSERT INTO ventas no nombra las columnas nuevas: el DEFAULT false y
--     el NULL cumplen el CHECK;
--   - su INSERT INTO venta_detalle siempre trae lote y costo;
--   - lee venta_detalle con getLong/getBigDecimal: una línea sin lote (solo
--     existe si el código nuevo alcanzó a registrar una) le llega como 0/null,
--     y su reporte suma el costo con SUM, que ignora el NULL;
--   - no conoce revisiones_venta.
-- Probada en botica_prod_sim antes del deploy (R23), con los datos demo y el
-- jar de V12 arrancado sobre V13.

ALTER TABLE ventas ADD COLUMN vendida_sin_conexion BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE ventas ADD COLUMN hora_dispositivo TIMESTAMPTZ;
ALTER TABLE ventas ADD CONSTRAINT ventas_hora_dispositivo_solo_sin_conexion
    CHECK (vendida_sin_conexion OR hora_dispositivo IS NULL);

COMMENT ON COLUMN ventas.vendida_sin_conexion IS 'Tarea 22: true = el cajero la cobró sin conexión y llegó después desde la cola del dispositivo. Esas ventas nunca se rechazan por el negocio: cada problema queda en revisiones_venta.';
COMMENT ON COLUMN ventas.hora_dispositivo IS 'Tarea 22: hora del reloj del dispositivo al cobrar sin conexión, tal cual la mandó (puede estar mal). Solo auditoría: fecha es la hora del servidor al registrar y es la que usan el reporte y el Kardex. NULL en las ventas en línea.';

ALTER TABLE venta_detalle ALTER COLUMN lote_id DROP NOT NULL;
ALTER TABLE venta_detalle ALTER COLUMN costo_unitario DROP NOT NULL;
ALTER TABLE venta_detalle ADD CONSTRAINT venta_detalle_lote_y_costo_juntos
    CHECK ((lote_id IS NULL) = (costo_unitario IS NULL));

COMMENT ON COLUMN venta_detalle.lote_id IS 'Lote del que salió la línea (FEFO). NULL solo en una venta sin conexión (Tarea 22): las unidades entregadas sin stock vendible en el sistema; tienen su revisión STOCK_INSUFICIENTE y no mueven stock.';
COMMENT ON COLUMN venta_detalle.costo_unitario IS 'Costo del lote, congelado (D2). NULL junto con lote_id (Tarea 22): sin lote no hay costo conocido y no se inventa; el reporte de resultados no le suma costo.';

CREATE TABLE revisiones_venta (
    id                 BIGSERIAL PRIMARY KEY,
    botica_id          BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    venta_id           BIGINT NOT NULL REFERENCES ventas(id) ON DELETE RESTRICT,
    venta_detalle_id   BIGINT REFERENCES venta_detalle(id) ON DELETE RESTRICT,
    tipo               VARCHAR(20) NOT NULL
                       CHECK (tipo IN ('STOCK_INSUFICIENTE', 'PRECIO_DISTINTO', 'CAJA_CERRADA', 'OTRO')),
    detalle            VARCHAR(300) NOT NULL CHECK (length(trim(detalle)) > 0),
    precio_cobrado     NUMERIC(10, 2),
    precio_vigente     NUMERIC(10, 2),
    unidades_faltantes INTEGER CHECK (unidades_faltantes > 0),
    estado             VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'RESUELTA')),
    resolucion         VARCHAR(10) CHECK (resolucion IN ('VISTA', 'FALTANTE')),
    nota_resolucion    VARCHAR(300),
    resuelta_por       BIGINT REFERENCES usuarios(id) ON DELETE RESTRICT,
    resuelta_en        TIMESTAMPTZ,
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por         BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    CONSTRAINT revisiones_venta_resuelta_completa CHECK (
        (estado = 'RESUELTA') = (resolucion IS NOT NULL AND resuelta_por IS NOT NULL AND resuelta_en IS NOT NULL)),
    CONSTRAINT revisiones_venta_faltante_con_nota CHECK (
        resolucion IS DISTINCT FROM 'FALTANTE' OR length(trim(coalesce(nota_resolucion, ''))) > 0)
);

COMMENT ON TABLE revisiones_venta IS 'Tarea 22 (D-E2-7): un problema que el servidor encontró al registrar una venta sin conexión, que nunca se rechaza. STOCK_INSUFICIENTE (línea sin lote), PRECIO_DISTINTO (se respetó el precio cobrado), CAJA_CERRADA (llegó después del cierre de su caja) y OTRO (producto o presentación inactivos, receta faltante: el detalle lo dice). La resuelve el ADMINISTRADOR (VISTA, o FALTANTE con nota) y no se reabre. creado_por = quien hizo la venta.';
COMMENT ON COLUMN revisiones_venta.venta_detalle_id IS 'La línea sin lote, solo en STOCK_INSUFICIENTE.';
COMMENT ON COLUMN revisiones_venta.unidades_faltantes IS 'Unidades base sin stock vendible, solo en STOCK_INSUFICIENTE.';

CREATE INDEX idx_revisiones_venta_botica_estado ON revisiones_venta(botica_id, estado, creado_en);
CREATE INDEX idx_revisiones_venta_botica_venta ON revisiones_venta(botica_id, venta_id);
