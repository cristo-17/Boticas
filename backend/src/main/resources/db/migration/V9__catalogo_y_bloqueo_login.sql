-- ============================================================
-- V9 — catálogo editable y bloqueo de login (Tarea 17)
-- ============================================================
-- Corre sobre tablas CON filas (usuarios, productos y presentaciones de
-- la botica real). Todas las columnas nuevas tienen DEFAULT, así que el
-- ALTER rellena las filas existentes y no falla por NOT NULL. No cambia
-- ningún dato existente: solo agrega columnas y quita usuarios.turno.
--
-- Compatible hacia atrás: Render aplica la migración antes de cambiar de
-- versión. Si el deploy nuevo no arranca, la versión anterior sigue
-- sirviendo con este esquema: no lee usuarios.turno (el turno sale del
-- login, DECISIONES 2026-09-09), no inserta en usuarios, productos ni
-- presentaciones, y las columnas nuevas tienen DEFAULT.
-- Probada en botica_prod_sim antes del deploy (R23): camino de migraciones de
-- producción hasta V8 y filas con la forma de producción.

-- --- usuarios -----------------------------------------------------------

-- DIF-01: 5 intentos fallidos seguidos bloquean 15 minutos. El contador
-- se incrementa en SQL (intentos_fallidos = intentos_fallidos + 1), nunca
-- leyendo y escribiendo desde Java: dos intentos simultáneos cuentan dos.
ALTER TABLE usuarios
    ADD COLUMN intentos_fallidos INTEGER NOT NULL DEFAULT 0
        CONSTRAINT usuarios_intentos_fallidos_check CHECK (intentos_fallidos >= 0),
    ADD COLUMN bloqueado_hasta TIMESTAMPTZ;

COMMENT ON COLUMN usuarios.intentos_fallidos IS 'Intentos de login fallidos seguidos (DIF-01). Un login correcto lo vuelve a 0. Se incrementa en SQL, nunca con lectura y escritura desde Java.';
COMMENT ON COLUMN usuarios.bloqueado_hasta IS 'NULL = no bloqueado. Si es futuro, el login se rechaza con CUENTA_BLOQUEADA aunque la contraseña sea correcta. El administrador lo vuelve a NULL al desbloquear.';

-- D-F-2: sin lector. El turno real es el que se elige en cada login.
ALTER TABLE usuarios DROP COLUMN turno;

-- --- productos y presentaciones -----------------------------------------

-- Nunca se borra un producto: venta_detalle, lotes y movimientos_stock lo
-- referencian (RESTRICT). Desactivado no sale en Punto de Venta ni en el
-- alta de lote; sigue en Inventario, Alertas y Merma (tiene stock físico).
ALTER TABLE productos ADD COLUMN activo BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN productos.activo IS 'false = desactivado por el administrador: no se vende ni recibe lotes nuevos. Su historia (ventas, Kardex) y su stock siguen visibles; la merma lo puede retirar.';

-- Una presentación con ventas no se puede borrar (venta_detalle.presentacion_id
-- es RESTRICT). Quitarla al editar el producto la desactiva: deja de venderse,
-- y las ventas pasadas conservan etiqueta y precio congelados en venta_detalle.
ALTER TABLE presentaciones ADD COLUMN activo BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN presentaciones.activo IS 'false = quitada al editar el producto. No se vende. Nunca se borra: venta_detalle la referencia. La presentación con factor_conversion = 1 no se puede desactivar (regla del Service).';

-- Una sola fila de factor 1 por producto, activa o no: lotes, merma y alertas
-- la buscan con LEFT JOIN ... factor_conversion = 1, y una segunda fila
-- duplicaría filas en esos listados. El Service ya lo impide; el índice lo
-- garantiza aunque alguien inserte por SQL. Sin WHERE activo a propósito.
-- Falla si un producto ya tiene dos filas de factor 1: en producción hay 0
-- productos (verificado en botica_prod_sim); en dev, el seed tiene una por producto.
CREATE UNIQUE INDEX idx_presentaciones_unidad_unica ON presentaciones(producto_id)
    WHERE factor_conversion = 1;
