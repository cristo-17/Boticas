-- ============================================================
-- V1 — Esquema base de BoticaSys
-- Fase 1 (núcleo, se implementa ya) + Fase 2/3 (tablas creadas
-- vacías, sin código todavía).
--
-- Decisiones que dan forma a este esquema (docs/DECISIONES.md,
-- todas 2026-09-08):
--   D1 — multi-botica: botica_id NOT NULL en toda tabla operativa,
--        nunca lo manda el cliente (sale de ContextoOperacion, T8).
--   D2 — costo y ganancia: costo_unitario en lotes/venta_detalle,
--        capturado desde ahora aunque el reporte sea Fase 2.
--   D4 (corregida) — origen_captura: NOT NULL en venta_detalle,
--        NULLABLE en movimientos_stock (una merma no se busca ni se
--        escanea; ninguna columna de auditoría se rellena con un
--        valor de conveniencia).
--   hueco 10 — ventas.igv_tasa congelada.
--
-- Revisión de esquema (2026-09-08, antes de Tarea 8):
--   1. UNIQUE de lotes pasa de (botica_id, codigo) a
--      (botica_id, producto_id, codigo): el código lo asigna el
--      laboratorio, dos productos distintos pueden traer el mismo.
--   2. lotes.precio_unitario ELIMINADO. El precio de venta es del
--      catálogo (presentaciones.precio, fila con factor_conversion=1
--      = "Unidad"), no del lote: es lo que está en la etiqueta del
--      estante y no cambia por reposición. Solo el COSTO es del lote.
--      En el mock original lotes.precioUnitario duplicaba el precio
--      de la presentación Caja y MermaService lo multiplicaba por una
--      cantidad en unidades base -- un bug real heredado, corregido
--      de paso al eliminar la columna.
--   3. TIMESTAMP -> TIMESTAMPTZ en TODA columna de fecha+hora (nunca
--      TIMESTAMP simple + ALTER DATABASE ... SET timezone): con
--      TIMESTAMPTZ el valor almacenado es un instante inequívoco sin
--      importar la zona horaria de la sesión que escribe -- una
--      migración corrida desde pgAdmin, un backend en America/Lima y
--      un servidor de despliegue en UTC (Tarea 10) escriben el MISMO
--      instante. Con TIMESTAMP simple, cada uno interpreta now() en
--      su propia zona y el dato queda ambiguo. Detalle y alternativa
--      descartada en docs/DECISIONES.md. Las columnas de fecha de
--      NEGOCIO (fecha_vencimiento, caja_diaria.fecha, periodo) son
--      DATE, no les aplica esta discusión: un dia calendario no tiene
--      zona horaria propia, solo importa que la app calcule CUÁL dia
--      es con la fecha de Lima (Regla 5) antes de escribirlo.
--   4. ON DELETE CASCADE revisado FK por FK: cualquiera que colgara
--      directo de boticas (y por lo tanto pudiera borrar historial
--      completo de una botica con un DELETE accidental) pasa a
--      RESTRICT. Las boticas no se borran, se desactivan (columna
--      activo). Los CASCADE que sobreviven son composición real (una
--      fila que no tiene sentido sin su padre: presentaciones de un
--      producto, venta_detalle de una venta, movimientos_caja de una
--      caja, lotes_fraccionados de un lote) y su padre ya está
--      protegido de borrado accidental por otras FK en RESTRICT.
--
-- Nota sobre auditoría (Regla 9): creado_en/creado_por se agregan
-- donde la tabla no tiene ya una columna de negocio que responda
-- "quién" (ventas.usuario_id, caja_diaria.usuario_id, mermas.usuario_id
-- ya cumplen ese rol — no se duplica con un creado_por que valdría
-- exactamente lo mismo). movimientos_caja y movimientos_stock no
-- tienen esa columna de negocio, así que sí llevan creado_en+creado_por.
-- ============================================================

-- ============================================================
-- NÚCLEO (Fase 1)
-- ============================================================

CREATE TABLE boticas (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(150) NOT NULL,
    direccion   VARCHAR(250),
    ruc         VARCHAR(11),
    activo      BOOLEAN NOT NULL DEFAULT true,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE boticas IS 'Cada botica independiente que usa el sistema. Todo dato operativo cuelga de una botica (D1, multi-botica). NUNCA se borra: se desactiva con la columna activo. Por eso toda FK hacia esta tabla es ON DELETE RESTRICT, sin excepcion -- un DELETE accidental de una fila de boticas no debe poder arrastrar el historial completo de esa botica.';

CREATE TABLE roles (
    id        BIGSERIAL PRIMARY KEY,
    nombre    VARCHAR(30) NOT NULL UNIQUE CHECK (nombre IN ('TECNICO', 'ADMINISTRADOR')),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE roles IS 'Catálogo global de roles, no varía por botica.';

CREATE TABLE usuarios (
    id            BIGSERIAL PRIMARY KEY,
    botica_id     BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    rol_id        BIGINT NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    nombre        VARCHAR(150) NOT NULL,
    usuario       VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    turno         VARCHAR(10) NOT NULL CHECK (turno IN ('Mañana', 'Tarde', 'Noche')),
    activo        BOOLEAN NOT NULL DEFAULT true,
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por    BIGINT REFERENCES usuarios(id) ON DELETE SET NULL
);
COMMENT ON TABLE usuarios IS 'Personal de una botica. "usuario" (login) es único globalmente, no por botica: el login no pide elegir botica, se resuelve desde el usuario.';

CREATE INDEX idx_usuarios_botica ON usuarios(botica_id);

CREATE TABLE productos (
    id            BIGSERIAL PRIMARY KEY,
    botica_id     BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    nombre        VARCHAR(200) NOT NULL,
    laboratorio   VARCHAR(120),
    categoria     VARCHAR(80) NOT NULL,
    codigo_barras VARCHAR(20) NOT NULL,
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por    BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    UNIQUE (botica_id, codigo_barras)
);
COMMENT ON TABLE productos IS 'Catálogo de productos por botica (D1): cada botica tiene su propio catálogo, el mismo código de barras puede existir en dos boticas como productos distintos. alertaVencimiento del contrato es calculada, no una columna. botica_id es RESTRICT: ver comentario de la tabla boticas.';

CREATE TABLE presentaciones (
    id                 BIGSERIAL PRIMARY KEY,
    botica_id          BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    producto_id        BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
    etiqueta           VARCHAR(30) NOT NULL,
    factor_conversion  INTEGER NOT NULL CHECK (factor_conversion > 0),
    precio             NUMERIC(10, 2) NOT NULL CHECK (precio >= 0),
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE presentaciones IS 'Formas vendibles de un producto (caja/blíster/unidad). factor_conversion = unidades base que representa (caja=100, blister=10, unidad=1). La fila con factor_conversion=1 ("Unidad") es EL precio de venta por unidad base del producto -- lotes ya no tiene su propio precio (ver revisión #2). botica_id es redundante con productos.botica_id, incluido explícitamente por D1, y RESTRICT igual que productos. producto_id sí es CASCADE: una presentación no tiene sentido sin su producto, y un producto solo se puede borrar si nunca tuvo lotes/ventas (protegido por RESTRICT en esas tablas), así que este CASCADE nunca se lleva historial real.';

CREATE INDEX idx_presentaciones_producto ON presentaciones(producto_id);

CREATE TABLE lotes (
    id                BIGSERIAL PRIMARY KEY,
    botica_id         BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    producto_id       BIGINT NOT NULL REFERENCES productos(id) ON DELETE RESTRICT,
    codigo            VARCHAR(30) NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    stock             INTEGER NOT NULL CHECK (stock >= 0),
    ubicacion         VARCHAR(20),
    costo_unitario    NUMERIC(10, 2) NOT NULL CHECK (costo_unitario >= 0),
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por        BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    UNIQUE (botica_id, producto_id, codigo)
);
COMMENT ON TABLE lotes IS 'Lote físico en stock. costo_unitario (D2) se captura una vez y no se edita: es el dato que no se puede reconstruir después. El mismo producto se repone a costos distintos en cada lote, por eso el costo vive aquí y no en productos. NO tiene columna de precio de venta (revisión #2): el precio es del catálogo, ver presentaciones. UNIQUE es (botica_id, producto_id, codigo), no (botica_id, codigo): el código lo asigna el laboratorio y dos productos de fabricantes distintos pueden coincidir.';

CREATE INDEX idx_lotes_fefo ON lotes(botica_id, producto_id, fecha_vencimiento);

CREATE TABLE caja_diaria (
    id                  BIGSERIAL PRIMARY KEY,
    botica_id           BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    usuario_id          BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    fecha               DATE NOT NULL,
    turno               VARCHAR(10) NOT NULL CHECK (turno IN ('Mañana', 'Tarde', 'Noche')),
    monto_apertura      NUMERIC(10, 2) NOT NULL CHECK (monto_apertura >= 0),
    hora_apertura       TIMESTAMPTZ NOT NULL DEFAULT now(),
    hora_cierre         TIMESTAMPTZ,
    monto_contado       NUMERIC(10, 2),
    monto_esperado      NUMERIC(10, 2),
    diferencia          NUMERIC(10, 2),
    semaforo_descuadre  VARCHAR(10) CHECK (semaforo_descuadre IN ('EXACTO', 'LEVE', 'GRAVE')),
    observaciones       VARCHAR(500),
    estado              VARCHAR(10) NOT NULL DEFAULT 'ABIERTA' CHECK (estado IN ('ABIERTA', 'CERRADA'))
);
COMMENT ON TABLE caja_diaria IS 'Una caja por usuario/turno/día. monto_esperado/diferencia/semaforo_descuadre quedan NULL mientras está ABIERTA: conteo ciego (hueco 2) — el servidor no los calcula ni los expone hasta POST /api/caja/cerrar. estado (ABIERTA/CERRADA) es la representación en BD; el contrato JSON lo expone como el booleano "abierta". fecha es DATE de negocio (la fecha operativa de Lima, Regla 5) -- no participa de la discusión TIMESTAMPTZ.';

CREATE UNIQUE INDEX idx_caja_abierta_unica ON caja_diaria(botica_id, usuario_id, turno, fecha)
    WHERE estado = 'ABIERTA';

CREATE TABLE ventas (
    id                  BIGSERIAL PRIMARY KEY,
    botica_id           BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    usuario_id          BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    caja_id             BIGINT NOT NULL REFERENCES caja_diaria(id) ON DELETE RESTRICT,
    fecha               TIMESTAMPTZ NOT NULL DEFAULT now(),
    subtotal            NUMERIC(10, 2) NOT NULL CHECK (subtotal >= 0),
    igv                 NUMERIC(10, 2) NOT NULL CHECK (igv >= 0),
    igv_tasa            NUMERIC(5, 4) NOT NULL,
    total               NUMERIC(10, 2) NOT NULL CHECK (total >= 0),
    metodo_pago         VARCHAR(10) NOT NULL CHECK (metodo_pago IN ('efectivo', 'yape', 'tarjeta')),
    clave_idempotencia  UUID NOT NULL,
    sincronizada        BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (clave_idempotencia)
);
COMMENT ON TABLE ventas IS 'Cabecera de venta. clave_idempotencia la genera el frontend al confirmar el carrito (no en cada intento HTTP) — un reintento de red no duplica la venta. igv_tasa congela la tasa vigente al momento de la venta (hueco 10): /api/config puede cambiar mañana sin afectar ventas ya registradas. botica_id es RESTRICT: es el historial más crítico de todos, nunca se borra en cascada.';

CREATE INDEX idx_ventas_botica_fecha ON ventas(botica_id, fecha);

CREATE TABLE venta_detalle (
    id                    BIGSERIAL PRIMARY KEY,
    venta_id              BIGINT NOT NULL REFERENCES ventas(id) ON DELETE CASCADE,
    botica_id             BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    producto_id           BIGINT NOT NULL REFERENCES productos(id) ON DELETE RESTRICT,
    presentacion_id       BIGINT NOT NULL REFERENCES presentaciones(id) ON DELETE RESTRICT,
    lote_id               BIGINT NOT NULL REFERENCES lotes(id) ON DELETE RESTRICT,
    nombre_producto       VARCHAR(200) NOT NULL,
    etiqueta_presentacion VARCHAR(30) NOT NULL,
    cantidad              INTEGER NOT NULL CHECK (cantidad > 0),
    precio_unitario       NUMERIC(10, 2) NOT NULL CHECK (precio_unitario >= 0),
    costo_unitario        NUMERIC(10, 2) NOT NULL CHECK (costo_unitario >= 0),
    total_linea           NUMERIC(10, 2) NOT NULL CHECK (total_linea >= 0),
    origen_captura        VARCHAR(10) NOT NULL CHECK (origen_captura IN ('ESCANEO', 'MANUAL', 'BUSQUEDA'))
);
COMMENT ON TABLE venta_detalle IS 'Una fila por lote consumido (FEFO puede repartir una línea del carrito entre varios lotes, así que no es 1:1 con las líneas del carrito). precio_unitario y costo_unitario (D2) van CONGELADOS al momento de la venta — nunca una referencia al precio/costo actual. origen_captura (D4) es NOT NULL: en una venta siempre hay un origen real. venta_id es CASCADE (una línea no tiene sentido sin su venta); botica_id es RESTRICT.';

CREATE INDEX idx_venta_detalle_venta ON venta_detalle(venta_id);
CREATE INDEX idx_venta_detalle_lote ON venta_detalle(lote_id);

CREATE TABLE movimientos_caja (
    id              BIGSERIAL PRIMARY KEY,
    botica_id       BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    caja_id         BIGINT NOT NULL REFERENCES caja_diaria(id) ON DELETE CASCADE,
    tipo            VARCHAR(10) NOT NULL CHECK (tipo IN ('apertura', 'venta', 'ingreso', 'egreso', 'merma')),
    descripcion     VARCHAR(150) NOT NULL,
    nota            VARCHAR(250),
    monto           NUMERIC(10, 2) NOT NULL,
    afecta_efectivo BOOLEAN NOT NULL DEFAULT true,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por      BIGINT REFERENCES usuarios(id) ON DELETE SET NULL
);
COMMENT ON TABLE movimientos_caja IS 'Ledger de caja. monto puede ser negativo (egresos) — sin CHECK monto >= 0 a propósito, a diferencia de las demás columnas de dinero. tipo incluye EGRESO explícitamente (D2). afecta_efectivo=false en mermas y ventas con Yape/tarjeta. caja_id es CASCADE (un movimiento no tiene sentido sin su caja, y la caja ya está protegida de borrado accidental); botica_id es RESTRICT.';

CREATE INDEX idx_movimientos_caja_botica_fecha ON movimientos_caja(botica_id, creado_en);
CREATE INDEX idx_movimientos_caja_caja ON movimientos_caja(caja_id);

CREATE TABLE mermas (
    id          BIGSERIAL PRIMARY KEY,
    botica_id   BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    lote_id     BIGINT NOT NULL REFERENCES lotes(id) ON DELETE RESTRICT,
    usuario_id  BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    cantidad    INTEGER NOT NULL CHECK (cantidad > 0),
    motivo      VARCHAR(20) NOT NULL CHECK (motivo IN ('Vencimiento', 'Rotura', 'Deterioro', 'Robo o pérdida', 'Otro')),
    observacion VARCHAR(500),
    valor_venta NUMERIC(10, 2) NOT NULL CHECK (valor_venta >= 0),
    fecha       TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE mermas IS 'Baja de stock por vencimiento/rotura/etc. valor_venta (nunca "valor" a secas, nota 3) es a precio de venta, calculado con la presentación "Unidad" (factor_conversion=1) del producto del lote — el costo de la pérdida se calcula en el reporte de Fase 2 con JOIN a lotes.costo_unitario, sin columna propia aquí: el costo del lote no cambia después de creado, así que el JOIN siempre da el mismo resultado.';

CREATE INDEX idx_mermas_botica_fecha ON mermas(botica_id, fecha);
CREATE INDEX idx_mermas_lote ON mermas(lote_id);

CREATE TABLE movimientos_stock (
    id                    BIGSERIAL PRIMARY KEY,
    botica_id             BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    lote_id               BIGINT NOT NULL REFERENCES lotes(id) ON DELETE RESTRICT,
    tipo                  VARCHAR(10) NOT NULL CHECK (tipo IN ('VENTA', 'MERMA', 'INGRESO')),
    cantidad              INTEGER NOT NULL,
    origen_captura        VARCHAR(10) CHECK (origen_captura IN ('ESCANEO', 'MANUAL', 'BUSQUEDA')),
    referencia_venta_id   BIGINT REFERENCES ventas(id) ON DELETE SET NULL,
    referencia_merma_id   BIGINT REFERENCES mermas(id) ON DELETE SET NULL,
    creado_en             TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por            BIGINT REFERENCES usuarios(id) ON DELETE SET NULL
);
COMMENT ON TABLE movimientos_stock IS 'Ledger de stock (cantidad negativa = salida, positiva = ingreso). origen_captura (D4, corregido) es NULLABLE: NULL para las filas que genera una merma (no se busca ni se escanea un lote, se selecciona de una lista — no se inventa un valor de conveniencia). NOT NULL de facto para ventas, heredado de venta_detalle.origen_captura. botica_id es RESTRICT.';

CREATE INDEX idx_movimientos_stock_botica_lote ON movimientos_stock(botica_id, lote_id);

-- ============================================================
-- FUTURO (Fase 2/3 — tabla creada, sin código todavía)
-- ============================================================

CREATE TABLE clientes (
    id         BIGSERIAL PRIMARY KEY,
    botica_id  BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    nombre     VARCHAR(200) NOT NULL,
    documento  VARCHAR(20),
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE clientes IS 'FASE 2 — clientes frecuentes / historial de compra por cliente. Sin código ni endpoint todavía.';

CREATE TABLE comprobantes_electronicos (
    id         BIGSERIAL PRIMARY KEY,
    botica_id  BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    venta_id   BIGINT NOT NULL REFERENCES ventas(id) ON DELETE RESTRICT,
    tipo       VARCHAR(20),
    estado     VARCHAR(20),
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE comprobantes_electronicos IS 'FASE 3 — comprobante tributario SUNAT (boleta/factura electrónica con validación/envío/contingencia). Distinto del reporte de resultados interno de D2/D3 (docs/DECISIONES.md) — no confundir los dos términos. Endpoint futuro: POST /api/comprobantes.';

CREATE TABLE kardex (
    id           BIGSERIAL PRIMARY KEY,
    botica_id    BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    producto_id  BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE kardex IS 'FASE 2 — kardex valorizado por producto, consolidado desde movimientos_stock (dato rebuildable, no historial primario -- por eso producto_id sí es CASCADE). Sin código todavía. Endpoint futuro: GET /api/kardex.';

CREATE TABLE reportes_digemid (
    id         BIGSERIAL PRIMARY KEY,
    botica_id  BIGINT NOT NULL REFERENCES boticas(id) ON DELETE RESTRICT,
    periodo    DATE NOT NULL,
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE reportes_digemid IS 'FASE 3 — reporte periódico DIGEMID (control de estupefacientes/psicotrópicos). Sin código todavía. Endpoint futuro: GET /api/reportes/digemid. periodo es DATE de negocio, no participa de la discusión TIMESTAMPTZ.';

CREATE TABLE lotes_fraccionados (
    id                     BIGSERIAL PRIMARY KEY,
    lote_id                BIGINT NOT NULL REFERENCES lotes(id) ON DELETE CASCADE,
    unidades_fraccionadas  INTEGER NOT NULL CHECK (unidades_fraccionadas >= 0),
    creado_en              TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE lotes_fraccionados IS 'FASE 2 — venta fraccionada de un blíster/caja ya abierto. Sin código todavía. Endpoint futuro: PATCH /api/lotes/{id}/fraccionar. lote_id es CASCADE: es composición real de un lote que, para cuando se pudiera borrar, ya no tiene ninguna otra historia real (protegido por RESTRICT en venta_detalle/mermas/movimientos_stock).';
