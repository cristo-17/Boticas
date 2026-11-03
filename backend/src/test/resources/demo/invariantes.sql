-- Tarea D: invariantes de los datos demo. Cada consulta devuelve una fila
-- (invariante, fallas); fallas = 0 es verde. Solo miran las boticas demo (las
-- que tienen usuarios demo_*). Las leen DatosDemoInvariantesTest (JUnit) y psql
-- en botica_prod_sim y en la rama de Neon:
--   sed "s/{{DESCUADRE_LEVE}}/10.00/" invariantes.sql | psql "<url>" -At -F ' | ' | tr -d '\r'
-- {{DESCUADRE_LEVE}} = app.config-negocio.descuadre-leve (application.properties).
-- El 11 (botica real intacta) es md5-real.sql, antes y después de migrar. El 0 es una guardia:
-- si da distinto de 0, los demás no significan nada (no hay datos demo que mirar).

-- INV-0
-- Guardia: sin boticas demo, los demás invariantes dan 0 sin comprobar nada. Este falla si no
-- están las tres boticas demo, cada una con usuarios, productos y ventas, y la V12 registrada.
WITH demo AS (SELECT DISTINCT botica_id AS id FROM usuarios WHERE usuario LIKE 'demo\_%')
SELECT 'INV-0 están las 3 boticas demo con productos y ventas, y la V12 en flyway_schema_history' AS invariante,
  abs(3 - (SELECT count(*) FROM demo))
  + (SELECT count(*) FROM demo d WHERE NOT EXISTS (SELECT 1 FROM ventas v WHERE v.botica_id = d.id)
                                    OR NOT EXISTS (SELECT 1 FROM productos p WHERE p.botica_id = d.id))
  + (SELECT CASE WHEN EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '12' AND success) THEN 0 ELSE 1 END)
  AS fallas;

-- INV-1
WITH demo AS (SELECT DISTINCT botica_id AS id FROM usuarios WHERE usuario LIKE 'demo\_%')
SELECT 'INV-1 stock de cada lote = su entrada + movimientos, nunca negativo, entrada de un usuario de su botica' AS invariante,
  (SELECT count(*) FROM lotes l WHERE l.botica_id IN (SELECT id FROM demo)
     AND l.stock <> (SELECT COALESCE(SUM(m.cantidad), 0) FROM movimientos_stock m WHERE m.lote_id = l.id))
  + (SELECT count(*) FROM (SELECT SUM(m.cantidad) OVER (PARTITION BY m.lote_id ORDER BY m.creado_en, m.id) AS saldo
                           FROM movimientos_stock m WHERE m.botica_id IN (SELECT id FROM demo)) s WHERE s.saldo < 0)
  + (SELECT count(*) FROM lotes l WHERE l.botica_id IN (SELECT id FROM demo)
     AND NOT EXISTS (SELECT 1 FROM movimientos_stock m JOIN usuarios u ON u.id = m.creado_por
                     WHERE m.lote_id = l.id AND m.tipo = 'INGRESO' AND m.cantidad > 0
                       AND u.botica_id = l.botica_id
                       AND m.creado_en = (SELECT min(m2.creado_en) FROM movimientos_stock m2 WHERE m2.lote_id = l.id)))
  AS fallas;

-- INV-2
WITH demo AS (SELECT DISTINCT botica_id AS id FROM usuarios WHERE usuario LIKE 'demo\_%')
SELECT 'INV-2 saldo final del Kardex de cada producto = suma del stock de sus lotes' AS invariante,
  (SELECT count(*) FROM productos p WHERE p.botica_id IN (SELECT id FROM demo)
     AND (SELECT COALESCE(SUM(m.cantidad), 0) FROM movimientos_stock m JOIN lotes l ON l.id = m.lote_id WHERE l.producto_id = p.id)
      <> (SELECT COALESCE(SUM(l.stock), 0) FROM lotes l WHERE l.producto_id = p.id))
  AS fallas;

-- INV-3
WITH demo AS (SELECT DISTINCT botica_id AS id FROM usuarios WHERE usuario LIKE 'demo\_%')
SELECT 'INV-3 total = suma de líneas; IGV extraído (subtotal = total / (1 + tasa), igv = total - subtotal)' AS invariante,
  (SELECT count(*) FROM ventas v WHERE v.botica_id IN (SELECT id FROM demo)
     AND (v.total <> (SELECT SUM(vd.total_linea) FROM venta_detalle vd WHERE vd.venta_id = v.id)
          OR v.subtotal <> round(v.total / (1 + v.igv_tasa), 2)
          OR v.igv <> v.total - v.subtotal))
  AS fallas;

-- INV-4
WITH demo AS (SELECT DISTINCT botica_id AS id FROM usuarios WHERE usuario LIKE 'demo\_%')
SELECT 'INV-4 ninguna línea sale de un lote vencido y el lote es el que elegía FEFO' AS invariante,
  (SELECT count(*) FROM venta_detalle vd JOIN ventas v ON v.id = vd.venta_id JOIN lotes l ON l.id = vd.lote_id
   WHERE vd.botica_id IN (SELECT id FROM demo)
     AND l.fecha_vencimiento < (v.fecha AT TIME ZONE 'America/Lima')::date)
  + (SELECT count(*) FROM venta_detalle vd
       JOIN ventas v ON v.id = vd.venta_id
       JOIN lotes l ON l.id = vd.lote_id
       JOIN lotes l2 ON l2.producto_id = l.producto_id AND l2.id <> l.id
                    AND (l2.fecha_vencimiento, l2.id) < (l.fecha_vencimiento, l.id)
                    AND l2.fecha_vencimiento >= (v.fecha AT TIME ZONE 'America/Lima')::date
   WHERE vd.botica_id IN (SELECT id FROM demo)
     -- un lote anterior en el orden FEFO y vigente ese día tiene que quedar en 0 después de esta venta
     AND (SELECT COALESCE(SUM(m.cantidad), 0) FROM movimientos_stock m
          WHERE m.lote_id = l2.id AND m.creado_en <= v.fecha) > 0)
  AS fallas;

-- INV-5
WITH demo AS (SELECT DISTINCT botica_id AS id FROM usuarios WHERE usuario LIKE 'demo\_%')
SELECT 'INV-5 cierre: esperado = apertura + efectivo - egresos; diferencia y semáforo del servidor; GRAVE con su notificación' AS invariante,
  (SELECT count(*) FROM caja_diaria c WHERE c.botica_id IN (SELECT id FROM demo) AND c.estado = 'CERRADA'
     AND (c.monto_esperado <> c.monto_apertura + (SELECT COALESCE(SUM(mc.monto), 0) FROM movimientos_caja mc
                                                  WHERE mc.caja_id = c.id AND mc.afecta_efectivo)
          OR c.diferencia <> c.monto_contado - c.monto_esperado
          OR c.semaforo_descuadre <> CASE WHEN c.diferencia = 0 THEN 'EXACTO'
                                          WHEN abs(c.diferencia) <= {{DESCUADRE_LEVE}} THEN 'LEVE'
                                          ELSE 'GRAVE' END
          OR c.hora_cierre IS NULL))
  + (SELECT count(*) FROM caja_diaria c WHERE c.botica_id IN (SELECT id FROM demo) AND c.estado = 'ABIERTA'
     AND (c.monto_esperado IS NOT NULL OR c.diferencia IS NOT NULL OR c.semaforo_descuadre IS NOT NULL OR c.hora_cierre IS NOT NULL))
  + (SELECT count(*) FROM caja_diaria c WHERE c.botica_id IN (SELECT id FROM demo) AND c.semaforo_descuadre = 'GRAVE'
     AND NOT EXISTS (SELECT 1 FROM notificaciones n WHERE n.botica_id = c.botica_id AND n.tipo = 'DESCUADRE_GRAVE'
                     AND n.rol_destinatario = 'ADMINISTRADOR' AND n.titulo = 'Descuadre grave en Caja #' || c.id))
  AS fallas;

-- INV-6
WITH demo AS (SELECT DISTINCT botica_id AS id FROM usuarios WHERE usuario LIKE 'demo\_%')
SELECT 'INV-6 cada venta dentro del horario de su caja y del dueño; cajas en su turno; nunca dos cajas abiertas del mismo usuario' AS invariante,
  (SELECT count(*) FROM ventas v JOIN caja_diaria c ON c.id = v.caja_id WHERE v.botica_id IN (SELECT id FROM demo)
     AND (v.usuario_id <> c.usuario_id OR v.fecha < c.hora_apertura OR v.fecha > COALESCE(c.hora_cierre, 'infinity')))
  + (SELECT count(*) FROM movimientos_caja mc JOIN caja_diaria c ON c.id = mc.caja_id WHERE mc.botica_id IN (SELECT id FROM demo)
     AND (mc.creado_por <> c.usuario_id OR mc.creado_en < c.hora_apertura OR mc.creado_en > COALESCE(c.hora_cierre, 'infinity')))
  + (SELECT count(*) FROM caja_diaria c WHERE c.botica_id IN (SELECT id FROM demo)
     AND (c.fecha <> (c.hora_apertura AT TIME ZONE 'America/Lima')::date
          OR extract(hour FROM c.hora_apertura AT TIME ZONE 'America/Lima')
             <> CASE c.turno WHEN 'Mañana' THEN 7 WHEN 'Tarde' THEN 14 ELSE 22 END))
  + (SELECT count(*) FROM caja_diaria a JOIN caja_diaria b ON a.usuario_id = b.usuario_id AND a.id < b.id
     WHERE a.botica_id IN (SELECT id FROM demo)
       AND tstzrange(a.hora_apertura, COALESCE(a.hora_cierre, 'infinity')) && tstzrange(b.hora_apertura, COALESCE(b.hora_cierre, 'infinity')))
  + (SELECT count(*) FROM ventas v WHERE v.botica_id IN (SELECT id FROM demo)
     AND v.fecha >= date_trunc('day', now() AT TIME ZONE 'America/Lima') AT TIME ZONE 'America/Lima')
  AS fallas;

-- INV-7
-- No hay historial de precios: el cambio se reconoce en las líneas. Un producto cambia el precio
-- de todas sus presentaciones a la vez; su "corte" es la primera línea con precio distinto del
-- anterior. Antes del corte vale el precio viejo; desde el corte, solo el actual. Un producto sin
-- corte solo puede tener el precio actual. Una presentación que no se vendió después del corte
-- conserva bien el precio viejo en su última línea.
WITH demo AS (SELECT DISTINCT botica_id AS id FROM usuarios WHERE usuario LIKE 'demo\_%'),
lineas AS (
  SELECT vd.producto_id, vd.presentacion_id, vd.precio_unitario, v.fecha,
         round(pr.precio / pr.factor_conversion, 2) AS actual,
         lag(vd.precio_unitario) OVER (PARTITION BY vd.presentacion_id ORDER BY v.fecha, vd.id) AS anterior
  FROM venta_detalle vd JOIN ventas v ON v.id = vd.venta_id JOIN presentaciones pr ON pr.id = vd.presentacion_id
  WHERE vd.botica_id IN (SELECT id FROM demo)),
corte AS (SELECT producto_id, min(fecha) AS t FROM lineas WHERE anterior IS NOT NULL AND anterior <> precio_unitario GROUP BY producto_id)
SELECT 'INV-7 precio congelado = vigente: un solo corte por producto; desde el corte, solo el precio actual' AS invariante,
  (SELECT count(*) FROM (SELECT presentacion_id FROM lineas GROUP BY presentacion_id
                         HAVING count(*) FILTER (WHERE anterior IS NOT NULL AND anterior <> precio_unitario) > 1) x)
  + (SELECT count(*) FROM lineas li LEFT JOIN corte c ON c.producto_id = li.producto_id
     WHERE li.precio_unitario <> li.actual AND (c.t IS NULL OR li.fecha >= c.t))
  + (SELECT count(*) FROM lineas li JOIN corte c ON c.producto_id = li.producto_id
     WHERE li.fecha < c.t AND li.precio_unitario = li.actual
       AND EXISTS (SELECT 1 FROM lineas l2 WHERE l2.presentacion_id = li.presentacion_id AND l2.precio_unitario <> l2.actual))
  AS fallas;

-- INV-8
WITH demo AS (SELECT DISTINCT botica_id AS id FROM usuarios WHERE usuario LIKE 'demo\_%')
SELECT 'INV-8 cada línea congela requiere_receta del producto; toda venta con una línea así tiene receta, y solo esas' AS invariante,
  (SELECT count(*) FROM venta_detalle vd JOIN productos p ON p.id = vd.producto_id
   WHERE vd.botica_id IN (SELECT id FROM demo) AND vd.requiere_receta <> p.requiere_receta)
  + (SELECT count(*) FROM ventas v WHERE v.botica_id IN (SELECT id FROM demo)
     AND EXISTS (SELECT 1 FROM venta_detalle vd WHERE vd.venta_id = v.id AND vd.requiere_receta)
     AND NOT EXISTS (SELECT 1 FROM recetas r WHERE r.venta_id = v.id))
  + (SELECT count(*) FROM recetas r WHERE r.botica_id IN (SELECT id FROM demo)
     AND NOT EXISTS (SELECT 1 FROM venta_detalle vd WHERE vd.venta_id = r.venta_id AND vd.requiere_receta))
  AS fallas;

-- INV-9
WITH demo AS (SELECT DISTINCT botica_id AS id FROM usuarios WHERE usuario LIKE 'demo\_%')
SELECT 'INV-9 en toda la cadena de FKs cada dato cuelga de la misma botica' AS invariante,
  (SELECT count(*) FROM presentaciones x JOIN productos p ON p.id = x.producto_id
   WHERE x.botica_id IN (SELECT id FROM demo) AND x.botica_id <> p.botica_id)
  + (SELECT count(*) FROM productos x JOIN usuarios u ON u.id = x.creado_por
     WHERE x.botica_id IN (SELECT id FROM demo) AND x.botica_id <> u.botica_id)
  + (SELECT count(*) FROM lotes x JOIN productos p ON p.id = x.producto_id JOIN usuarios u ON u.id = x.creado_por
     WHERE x.botica_id IN (SELECT id FROM demo) AND (x.botica_id <> p.botica_id OR x.botica_id <> u.botica_id))
  + (SELECT count(*) FROM caja_diaria x JOIN usuarios u ON u.id = x.usuario_id
     WHERE x.botica_id IN (SELECT id FROM demo) AND x.botica_id <> u.botica_id)
  + (SELECT count(*) FROM ventas x JOIN caja_diaria c ON c.id = x.caja_id JOIN usuarios u ON u.id = x.usuario_id
     WHERE x.botica_id IN (SELECT id FROM demo) AND (x.botica_id <> c.botica_id OR x.botica_id <> u.botica_id))
  + (SELECT count(*) FROM venta_detalle x
       JOIN ventas v ON v.id = x.venta_id JOIN lotes l ON l.id = x.lote_id
       JOIN productos p ON p.id = x.producto_id JOIN presentaciones pr ON pr.id = x.presentacion_id
     WHERE x.botica_id IN (SELECT id FROM demo)
       AND (x.botica_id <> v.botica_id OR x.botica_id <> l.botica_id OR x.botica_id <> p.botica_id
            OR x.botica_id <> pr.botica_id OR l.producto_id <> x.producto_id OR pr.producto_id <> x.producto_id))
  + (SELECT count(*) FROM recetas x JOIN ventas v ON v.id = x.venta_id JOIN usuarios u ON u.id = x.creado_por
     WHERE x.botica_id IN (SELECT id FROM demo) AND (x.botica_id <> v.botica_id OR x.botica_id <> u.botica_id))
  + (SELECT count(*) FROM mermas x JOIN lotes l ON l.id = x.lote_id JOIN usuarios u ON u.id = x.usuario_id
     WHERE x.botica_id IN (SELECT id FROM demo) AND (x.botica_id <> l.botica_id OR x.botica_id <> u.botica_id))
  + (SELECT count(*) FROM movimientos_caja x JOIN caja_diaria c ON c.id = x.caja_id JOIN usuarios u ON u.id = x.creado_por
     WHERE x.botica_id IN (SELECT id FROM demo) AND (x.botica_id <> c.botica_id OR x.botica_id <> u.botica_id))
  + (SELECT count(*) FROM movimientos_stock x JOIN lotes l ON l.id = x.lote_id JOIN usuarios u ON u.id = x.creado_por
       LEFT JOIN ventas v ON v.id = x.referencia_venta_id LEFT JOIN mermas m ON m.id = x.referencia_merma_id
     WHERE x.botica_id IN (SELECT id FROM demo)
       AND (x.botica_id <> l.botica_id OR x.botica_id <> u.botica_id
            OR x.botica_id <> COALESCE(v.botica_id, x.botica_id) OR x.botica_id <> COALESCE(m.botica_id, x.botica_id)))
  AS fallas;

-- INV-10
WITH demo AS (SELECT DISTINCT botica_id AS id FROM usuarios WHERE usuario LIKE 'demo\_%'),
por_dia AS (SELECT botica_id, (fecha AT TIME ZONE 'America/Lima')::date AS dia, SUM(total) AS ventas
            FROM ventas WHERE botica_id IN (SELECT id FROM demo) GROUP BY 1, 2),
por_caja AS (SELECT mc.botica_id, SUM(mc.monto) AS ventas FROM movimientos_caja mc
             WHERE mc.botica_id IN (SELECT id FROM demo) AND mc.tipo = 'venta' GROUP BY 1)
SELECT 'INV-10 la suma por día de Lima del rango completo = la suma de todas las cajas (la Noche se reparte en dos días)' AS invariante,
  (SELECT count(*) FROM por_caja pc
   WHERE pc.ventas <> (SELECT COALESCE(SUM(pd.ventas), 0) FROM por_dia pd WHERE pd.botica_id = pc.botica_id))
  + (SELECT count(*) FROM demo WHERE id NOT IN (SELECT botica_id FROM por_caja))
  AS fallas;
