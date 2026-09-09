-- ============================================================
-- V2 — Datos de demostración
-- Dos boticas con datos deliberadamente distintos (D1) — no dos
-- copias idénticas con otro id. Contraseñas en texto plano para
-- entrar: TECNICO -> tecnico123, ADMINISTRADOR -> admin123
-- (mismas para ambas boticas, hasheadas con BCrypt abajo).
--
-- Revisión 2026-09-08: ya no hay lotes.precio_unitario (el precio de
-- venta es de presentaciones, fila con factor_conversion=1); todo
-- literal de fecha+hora usa AT TIME ZONE 'America/Lima' explícito
-- para no depender de la zona horaria de la sesión que corre esta
-- migración (la misma trampa que se evita con TIMESTAMPTZ en V1).
-- ============================================================

INSERT INTO boticas (nombre, direccion, ruc, activo) VALUES
    ('Botica San Lucas', 'Av. Grau 412, Chiclayo', '20123456789', true),
    ('Botica Vida Sana', 'Jr. Independencia 245, San Juan de Lurigancho', '20987654321', true);

INSERT INTO roles (nombre) VALUES ('TECNICO'), ('ADMINISTRADOR');

-- Hashes BCrypt reales, generados con BCryptPasswordEncoder (no inventados)
INSERT INTO usuarios (botica_id, rol_id, nombre, usuario, password_hash, turno) VALUES
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'),
     (SELECT id FROM roles WHERE nombre = 'TECNICO'),
     'Rosa Quispe', 'rosa.quispe',
     '$2a$10$yhobjyrExrEm5GaS7rFfiOD51CXwqBYPLBl3cP4ZfJw35t4dNsfEG', 'Tarde'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'),
     (SELECT id FROM roles WHERE nombre = 'ADMINISTRADOR'),
     'Carlos Mendoza', 'carlos.mendoza',
     '$2a$10$sEfSijDqVDlPVR2XIpJtROeK60v7zhvXflW5UhY9XkgH91LZ3xPQa', 'Mañana'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'),
     (SELECT id FROM roles WHERE nombre = 'TECNICO'),
     'Milagros Torres', 'milagros.torres',
     '$2a$10$yhobjyrExrEm5GaS7rFfiOD51CXwqBYPLBl3cP4ZfJw35t4dNsfEG', 'Noche'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'),
     (SELECT id FROM roles WHERE nombre = 'ADMINISTRADOR'),
     'Jorge Fernández', 'jorge.fernandez',
     '$2a$10$sEfSijDqVDlPVR2XIpJtROeK60v7zhvXflW5UhY9XkgH91LZ3xPQa', 'Tarde');

-- ============================================================
-- Productos — mismo catálogo "de referencia" en ambas boticas
-- (nombres reales de botica peruana), pero cada fila es un
-- producto propio de su botica (D1: catálogos independientes).
-- ============================================================

-- unidad_nombre (V3, columna NOT NULL sin default) -- sustantivo real
-- de la unidad base de cada producto, usado por el frontend para
-- componer "100 tabletas"/"1 cápsula"/etc. a partir de factorConversion.
INSERT INTO productos (botica_id, nombre, laboratorio, categoria, codigo_barras, unidad_nombre) VALUES
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Paracetamol 500 mg', 'Genfar', 'Analgésicos', '7751234000118', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Amoxicilina 500 mg', 'Portugal', 'Antibióticos', '7751234000217', 'cápsula'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Ibuprofeno 400 mg', 'Medifarma', 'Analgésicos', '7751234000316', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Loratadina 10 mg', 'Genfar', 'Antialérgicos', '7751234000415', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Omeprazol 20 mg', 'Unimed', 'Gastrointestinal', '7751234000514', 'cápsula'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Sales de rehidratación', 'Farmindustria', 'Otros', '7751234000613', 'sobre'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Clotrimazol crema 20 g', 'Medifarma', 'Dermatológicos', '7751234000712', 'tubo'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Metformina 850 mg', 'Genfar', 'Crónicos', '7751234000811', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Losartán 50 mg', 'Hersil', 'Crónicos', '7751234000910', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Azitromicina 500 mg', 'Medifarma', 'Antibióticos', '7751234001016', 'cápsula'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Diclofenaco 50 mg', 'Genfar', 'Analgésicos', '7751234001115', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Cetirizina 10 mg', 'Portugal', 'Antialérgicos', '7751234001214', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Ranitidina 150 mg', 'Unimed', 'Gastrointestinal', '7751234001313', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Vitamina C 500 mg', 'Farmindustria', 'Vitaminas', '7751234001412', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'), 'Salbutamol inhalador', 'Hersil', 'Respiratorio', '7751234001511', 'inhalador'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Paracetamol 500 mg', 'Genfar', 'Analgésicos', '7751234000118', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Amoxicilina 500 mg', 'Portugal', 'Antibióticos', '7751234000217', 'cápsula'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Ibuprofeno 400 mg', 'Medifarma', 'Analgésicos', '7751234000316', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Loratadina 10 mg', 'Genfar', 'Antialérgicos', '7751234000415', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Omeprazol 20 mg', 'Unimed', 'Gastrointestinal', '7751234000514', 'cápsula'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Sales de rehidratación', 'Farmindustria', 'Otros', '7751234000613', 'sobre'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Clotrimazol crema 20 g', 'Medifarma', 'Dermatológicos', '7751234000712', 'tubo'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Metformina 850 mg', 'Genfar', 'Crónicos', '7751234000811', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Losartán 50 mg', 'Hersil', 'Crónicos', '7751234000910', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Azitromicina 500 mg', 'Medifarma', 'Antibióticos', '7751234001016', 'cápsula'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Diclofenaco 50 mg', 'Genfar', 'Analgésicos', '7751234001115', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Cetirizina 10 mg', 'Portugal', 'Antialérgicos', '7751234001214', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Ranitidina 150 mg', 'Unimed', 'Gastrointestinal', '7751234001313', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Vitamina C 500 mg', 'Farmindustria', 'Vitaminas', '7751234001412', 'tableta'),
    ((SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'), 'Salbutamol inhalador', 'Hersil', 'Respiratorio', '7751234001511', 'inhalador');

-- Presentaciones: por código de barras (único por botica), evita depender de ids numéricos.
-- La fila con factor_conversion=1 ("Unidad") ES el precio de venta por unidad
-- base del producto -- lotes ya no tiene columna de precio propia.
DO $$
DECLARE
    v_botica_id BIGINT;
    v_datos RECORD;
BEGIN
    FOR v_botica_id IN SELECT id FROM boticas LOOP
        FOR v_datos IN SELECT * FROM (VALUES
            ('7751234000118', 'Caja', 100, 12.90), ('7751234000118', 'Blíster', 10, 1.80), ('7751234000118', 'Unidad', 1, 0.20),
            ('7751234000217', 'Caja', 50, 28.50), ('7751234000217', 'Blíster', 10, 6.50), ('7751234000217', 'Unidad', 1, 0.80),
            ('7751234000316', 'Caja', 100, 15.00), ('7751234000316', 'Blíster', 10, 2.20), ('7751234000316', 'Unidad', 1, 0.30),
            ('7751234000415', 'Caja', 30, 9.90), ('7751234000415', 'Blíster', 10, 3.50), ('7751234000415', 'Unidad', 1, 0.40),
            ('7751234000514', 'Caja', 30, 11.50), ('7751234000514', 'Blíster', 10, 4.20), ('7751234000514', 'Unidad', 1, 0.50),
            ('7751234000613', 'Caja', 12, 16.80), ('7751234000613', 'Unidad', 1, 1.60),
            ('7751234000712', 'Unidad', 1, 9.20),
            ('7751234000811', 'Caja', 60, 18.50), ('7751234000811', 'Blíster', 10, 3.40), ('7751234000811', 'Unidad', 1, 0.35),
            ('7751234000910', 'Caja', 30, 22.00), ('7751234000910', 'Blíster', 10, 8.00), ('7751234000910', 'Unidad', 1, 0.85),
            ('7751234001016', 'Caja', 3, 15.60), ('7751234001016', 'Unidad', 1, 5.50),
            ('7751234001115', 'Caja', 100, 13.20), ('7751234001115', 'Blíster', 10, 1.60), ('7751234001115', 'Unidad', 1, 0.18),
            ('7751234001214', 'Caja', 20, 10.80), ('7751234001214', 'Blíster', 10, 5.90), ('7751234001214', 'Unidad', 1, 0.65),
            ('7751234001313', 'Caja', 100, 14.50), ('7751234001313', 'Blíster', 10, 1.90), ('7751234001313', 'Unidad', 1, 0.22),
            ('7751234001412', 'Caja', 30, 12.00), ('7751234001412', 'Blíster', 10, 4.50), ('7751234001412', 'Unidad', 1, 0.50),
            ('7751234001511', 'Unidad', 1, 18.90)
        ) AS t(codigo_barras, etiqueta, factor_conversion, precio)
        LOOP
            INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio)
            SELECT v_botica_id, p.id, v_datos.etiqueta, v_datos.factor_conversion, v_datos.precio
            FROM productos p
            WHERE p.botica_id = v_botica_id AND p.codigo_barras = v_datos.codigo_barras;
        END LOOP;
    END LOOP;
END $$;

-- ============================================================
-- Lotes — 3 por producto (45 por botica, 90 en total: sobra para
-- 3 páginas de tamaño 20). Vencimiento y stock en un patrón
-- DETERMINISTA (no random()): reconstruir la base desde cero debe
-- dar siempre los mismos datos, no un mueble en movimiento cada vez
-- que se prueba algo. Sin columna de precio: el precio de venta sale
-- de presentaciones (revisión #2).
-- ============================================================

DO $$
DECLARE
    v_botica_id BIGINT;
    v_producto RECORD;
    v_lote_n INT;
    v_dias INT;
    v_stock INT;
    v_costo NUMERIC(10, 2);
    v_ubicaciones TEXT[] := ARRAY['A-1', 'A-2', 'A-3', 'B-1', 'B-2', 'B-3', 'C-1', 'C-2', 'D-1'];
BEGIN
    FOR v_botica_id IN SELECT id FROM boticas ORDER BY id LOOP
        FOR v_producto IN SELECT id FROM productos WHERE botica_id = v_botica_id ORDER BY id LOOP
            FOR v_lote_n IN 1..3 LOOP
                -- Vencimiento: cicla por los 4 estados (VENCIDO/CRITICO/ADVERTENCIA/OK)
                v_dias := CASE (v_producto.id + v_lote_n) % 4
                    WHEN 0 THEN -15
                    WHEN 1 THEN 12
                    WHEN 2 THEN 55
                    ELSE 220
                END;
                -- Stock: cicla por AGOTADO/CRITICO(x2)/OK(x3), independiente del ciclo de vencimiento
                v_stock := CASE (v_producto.id + v_lote_n) % 6
                    WHEN 0 THEN 0
                    WHEN 1 THEN 8
                    WHEN 2 THEN 15
                    WHEN 3 THEN 40
                    WHEN 4 THEN 90
                    ELSE 180
                END;
                -- Costo: ~62% de un precio de referencia determinista (no depende de presentaciones)
                v_costo := round((5.00 + (((v_producto.id * 7 + v_lote_n * 3) % 30))::numeric) * 0.62, 2);

                INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, ubicacion, costo_unitario)
                VALUES (
                    v_botica_id, v_producto.id,
                    'L-' || v_producto.id || '-' || v_lote_n,
                    (CURRENT_DATE + v_dias)::date,
                    v_stock,
                    v_ubicaciones[1 + ((v_producto.id + v_lote_n) % array_length(v_ubicaciones, 1))],
                    v_costo
                );
            END LOOP;
        END LOOP;
    END LOOP;
END $$;

-- Caso deliberado para el UNIQUE (botica_id, producto_id, codigo): el mismo
-- código de lote asignado por el laboratorio a dos productos distintos de
-- la misma botica. Con el UNIQUE viejo (botica_id, codigo) esto habría
-- violado la restricción; con el nuevo, son lotes válidos y distintos.
DO $$
DECLARE
    v_botica_id BIGINT;
    v_producto_paracetamol BIGINT;
    v_producto_ibuprofeno BIGINT;
BEGIN
    v_botica_id := (SELECT id FROM boticas WHERE nombre = 'Botica San Lucas');
    v_producto_paracetamol := (SELECT id FROM productos WHERE botica_id = v_botica_id AND codigo_barras = '7751234000118');
    v_producto_ibuprofeno := (SELECT id FROM productos WHERE botica_id = v_botica_id AND codigo_barras = '7751234000316');

    INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, ubicacion, costo_unitario) VALUES
        (v_botica_id, v_producto_paracetamol, 'LAB-COMPARTIDO-01', CURRENT_DATE + 150, 60, 'A-9', 3.10),
        (v_botica_id, v_producto_ibuprofeno, 'LAB-COMPARTIDO-01', CURRENT_DATE + 150, 60, 'A-9', 4.40);
END $$;

-- ============================================================
-- Caja cerrada de ayer, con movimientos y ventas — una por botica,
-- con montos distintos a propósito (D1: nada idéntico entre boticas
-- salvo el id). Botica San Lucas cierra EXACTA; Botica Vida Sana
-- cierra con descuadre LEVE.
--
-- "Ayer" y las horas se anclan a America/Lima explícitamente (AT TIME
-- ZONE), no a CURRENT_DATE/CURRENT_TIME de la sesión que corre la
-- migración -- exactamente la trampa que motivó pasar a TIMESTAMPTZ.
-- ============================================================

-- Cada venta seedeada es internamente consistente (subtotal/igv/total y
-- total_linea cuadran exactamente entre sí, Anexo C regla 9). El
-- movimiento de caja tipo 'venta' representa esa misma venta 1:1 (una
-- venta real crea un solo movimiento de caja — Tarea 11 Bloque B — así
-- que aquí no hay una "venta agregada del día" ni boletas fantasma).
DO $$
DECLARE
    v_hoy_lima DATE := (now() AT TIME ZONE 'America/Lima')::date;
    v_ayer_lima DATE := v_hoy_lima - 1;
    v_botica_id BIGINT;
    v_tecnico_id BIGINT;
    v_caja_id BIGINT;
    v_venta_id BIGINT;
    v_lote_id BIGINT;
    v_producto_id BIGINT;
    v_presentacion_id BIGINT;
    v_precio_unidad NUMERIC(10, 2);
BEGIN
    -- ===== Botica San Lucas: caja EXACTA =====
    v_botica_id := (SELECT id FROM boticas WHERE nombre = 'Botica San Lucas');
    v_tecnico_id := (SELECT id FROM usuarios WHERE usuario = 'rosa.quispe');

    -- Venta: 4 Blíster de Paracetamol @ 1.80 = 7.20 (total = subtotal, igv se extrae de adentro)
    INSERT INTO caja_diaria (botica_id, usuario_id, fecha, turno, monto_apertura, hora_apertura, hora_cierre,
                              monto_contado, monto_esperado, diferencia, semaforo_descuadre, estado)
    VALUES (v_botica_id, v_tecnico_id, v_ayer_lima, 'Tarde', 100.00,
            (v_ayer_lima + TIME '14:02') AT TIME ZONE 'America/Lima',
            (v_ayer_lima + TIME '22:05') AT TIME ZONE 'America/Lima',
            112.20, 112.20, 0.00, 'EXACTO', 'CERRADA')
    RETURNING id INTO v_caja_id;

    INSERT INTO movimientos_caja (botica_id, caja_id, tipo, descripcion, nota, monto, afecta_efectivo, creado_por) VALUES
        (v_botica_id, v_caja_id, 'venta', 'Ventas en efectivo', '1 boleta (venta de ejemplo)', 7.20, true, v_tecnico_id),
        (v_botica_id, v_caja_id, 'ingreso', 'Ingresos extra', 'Vuelto de proveedor', 20.00, true, v_tecnico_id),
        (v_botica_id, v_caja_id, 'egreso', 'Egresos', 'Compra de bolsas y útiles de limpieza', -15.00, true, v_tecnico_id);

    SELECT p.id, pr.id INTO v_producto_id, v_presentacion_id
    FROM productos p JOIN presentaciones pr ON pr.producto_id = p.id
    WHERE p.botica_id = v_botica_id AND p.codigo_barras = '7751234000118' AND pr.etiqueta = 'Blíster';
    SELECT id INTO v_lote_id FROM lotes WHERE botica_id = v_botica_id AND producto_id = v_producto_id AND stock > 20 ORDER BY fecha_vencimiento LIMIT 1;

    INSERT INTO ventas (botica_id, usuario_id, caja_id, fecha, subtotal, igv, igv_tasa, total, metodo_pago, clave_idempotencia, sincronizada)
    VALUES (v_botica_id, v_tecnico_id, v_caja_id, (v_ayer_lima + TIME '16:40') AT TIME ZONE 'America/Lima',
            7.20, 1.10, 0.1800, 7.20, 'efectivo', gen_random_uuid(), true)
    RETURNING id INTO v_venta_id;

    INSERT INTO venta_detalle (venta_id, botica_id, producto_id, presentacion_id, lote_id, nombre_producto, etiqueta_presentacion,
                                cantidad, precio_unitario, costo_unitario, total_linea, origen_captura)
    SELECT v_venta_id, v_botica_id, v_producto_id, v_presentacion_id, v_lote_id, 'Paracetamol 500 mg', 'Blíster',
           4, 1.80, l.costo_unitario, 7.20, 'BUSQUEDA'
    FROM lotes l WHERE l.id = v_lote_id;

    INSERT INTO movimientos_stock (botica_id, lote_id, tipo, cantidad, origen_captura, referencia_venta_id, creado_por)
    VALUES (v_botica_id, v_lote_id, 'VENTA', -4, 'BUSQUEDA', v_venta_id, v_tecnico_id);

    -- Una merma de ayer, valorizada a precio de venta = precio de la presentación
    -- "Unidad" (factor_conversion=1) del producto del lote -- ya no lotes.precio_unitario.
    SELECT id INTO v_lote_id FROM lotes WHERE botica_id = v_botica_id AND fecha_vencimiento < v_hoy_lima ORDER BY id LIMIT 1;
    SELECT pr.precio INTO v_precio_unidad
    FROM lotes l JOIN presentaciones pr ON pr.producto_id = l.producto_id
    WHERE l.id = v_lote_id AND pr.factor_conversion = 1;
    INSERT INTO mermas (botica_id, lote_id, usuario_id, cantidad, motivo, observacion, valor_venta, fecha)
    VALUES (v_botica_id, v_lote_id, v_tecnico_id, 3, 'Vencimiento', NULL, round(3 * v_precio_unidad, 2),
            (v_ayer_lima + TIME '11:15') AT TIME ZONE 'America/Lima');
    INSERT INTO movimientos_stock (botica_id, lote_id, tipo, cantidad, origen_captura, referencia_merma_id, creado_por)
    SELECT v_botica_id, v_lote_id, 'MERMA', -3, NULL, m.id, v_tecnico_id
    FROM mermas m WHERE m.lote_id = v_lote_id ORDER BY m.id DESC LIMIT 1;

    -- ===== Botica Vida Sana: caja con descuadre LEVE =====
    v_botica_id := (SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana');
    v_tecnico_id := (SELECT id FROM usuarios WHERE usuario = 'milagros.torres');

    -- Venta: 1 Caja de Ibuprofeno @ 15.00 = 15.00
    INSERT INTO caja_diaria (botica_id, usuario_id, fecha, turno, monto_apertura, hora_apertura, hora_cierre,
                              monto_contado, monto_esperado, diferencia, semaforo_descuadre, estado)
    VALUES (v_botica_id, v_tecnico_id, v_ayer_lima, 'Noche', 150.00,
            (v_ayer_lima + TIME '22:00') AT TIME ZONE 'America/Lima',
            (v_hoy_lima + TIME '06:10') AT TIME ZONE 'America/Lima',
            150.00, 155.00, -5.00, 'LEVE', 'CERRADA')
    RETURNING id INTO v_caja_id;

    INSERT INTO movimientos_caja (botica_id, caja_id, tipo, descripcion, nota, monto, afecta_efectivo, creado_por) VALUES
        (v_botica_id, v_caja_id, 'venta', 'Ventas en efectivo', '1 boleta (venta de ejemplo)', 15.00, true, v_tecnico_id),
        (v_botica_id, v_caja_id, 'egreso', 'Egresos', 'Movilidad del turno noche', -10.00, true, v_tecnico_id);

    SELECT p.id, pr.id INTO v_producto_id, v_presentacion_id
    FROM productos p JOIN presentaciones pr ON pr.producto_id = p.id
    WHERE p.botica_id = v_botica_id AND p.codigo_barras = '7751234000316' AND pr.etiqueta = 'Caja';
    SELECT id INTO v_lote_id FROM lotes WHERE botica_id = v_botica_id AND producto_id = v_producto_id AND stock > 20 ORDER BY fecha_vencimiento LIMIT 1;

    INSERT INTO ventas (botica_id, usuario_id, caja_id, fecha, subtotal, igv, igv_tasa, total, metodo_pago, clave_idempotencia, sincronizada)
    VALUES (v_botica_id, v_tecnico_id, v_caja_id, (v_ayer_lima + TIME '23:10') AT TIME ZONE 'America/Lima',
            15.00, 2.29, 0.1800, 15.00, 'efectivo', gen_random_uuid(), true)
    RETURNING id INTO v_venta_id;

    INSERT INTO venta_detalle (venta_id, botica_id, producto_id, presentacion_id, lote_id, nombre_producto, etiqueta_presentacion,
                                cantidad, precio_unitario, costo_unitario, total_linea, origen_captura)
    SELECT v_venta_id, v_botica_id, v_producto_id, v_presentacion_id, v_lote_id, 'Ibuprofeno 400 mg', 'Caja',
           1, 15.00, l.costo_unitario, 15.00, 'ESCANEO'
    FROM lotes l WHERE l.id = v_lote_id;

    INSERT INTO movimientos_stock (botica_id, lote_id, tipo, cantidad, origen_captura, referencia_venta_id, creado_por)
    VALUES (v_botica_id, v_lote_id, 'VENTA', -1, 'ESCANEO', v_venta_id, v_tecnico_id);

    SELECT id INTO v_lote_id FROM lotes WHERE botica_id = v_botica_id AND fecha_vencimiento < v_hoy_lima ORDER BY id LIMIT 1;
    SELECT pr.precio INTO v_precio_unidad
    FROM lotes l JOIN presentaciones pr ON pr.producto_id = l.producto_id
    WHERE l.id = v_lote_id AND pr.factor_conversion = 1;
    INSERT INTO mermas (botica_id, lote_id, usuario_id, cantidad, motivo, observacion, valor_venta, fecha)
    VALUES (v_botica_id, v_lote_id, v_tecnico_id, 2, 'Otro', 'Empaque dañado en traslado', round(2 * v_precio_unidad, 2),
            (v_ayer_lima + TIME '09:40') AT TIME ZONE 'America/Lima');
    INSERT INTO movimientos_stock (botica_id, lote_id, tipo, cantidad, origen_captura, referencia_merma_id, creado_por)
    SELECT v_botica_id, v_lote_id, 'MERMA', -2, NULL, m.id, v_tecnico_id
    FROM mermas m WHERE m.lote_id = v_lote_id ORDER BY m.id DESC LIMIT 1;
END $$;
