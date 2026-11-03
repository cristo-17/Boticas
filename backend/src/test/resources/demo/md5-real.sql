-- Tarea D, invariante 11: huella de las filas que NO son de las boticas demo
-- (la botica real en producción). Se corre antes y después de la V12 y las dos
-- salidas tienen que ser idénticas. Antes de la V12 no hay boticas demo, así que
-- cubre todas las filas.
--   psql "<url>" -At -F ' | ' -f md5-real.sql | tr -d '\r' > antes.txt   (y después.txt; diff)
WITH demo AS (SELECT DISTINCT botica_id AS id FROM usuarios WHERE usuario LIKE 'demo\_%')
SELECT 'boticas', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM boticas t WHERE t.id NOT IN (SELECT id FROM demo)
UNION ALL SELECT 'roles', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM roles t
UNION ALL SELECT 'usuarios', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM usuarios t WHERE t.botica_id NOT IN (SELECT id FROM demo)
UNION ALL SELECT 'productos', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM productos t WHERE t.botica_id NOT IN (SELECT id FROM demo)
UNION ALL SELECT 'presentaciones', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM presentaciones t WHERE t.botica_id NOT IN (SELECT id FROM demo)
UNION ALL SELECT 'lotes', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM lotes t WHERE t.botica_id NOT IN (SELECT id FROM demo)
UNION ALL SELECT 'caja_diaria', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM caja_diaria t WHERE t.botica_id NOT IN (SELECT id FROM demo)
UNION ALL SELECT 'ventas', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM ventas t WHERE t.botica_id NOT IN (SELECT id FROM demo)
UNION ALL SELECT 'venta_detalle', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM venta_detalle t WHERE t.botica_id NOT IN (SELECT id FROM demo)
UNION ALL SELECT 'recetas', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM recetas t WHERE t.botica_id NOT IN (SELECT id FROM demo)
UNION ALL SELECT 'mermas', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM mermas t WHERE t.botica_id NOT IN (SELECT id FROM demo)
UNION ALL SELECT 'movimientos_caja', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM movimientos_caja t WHERE t.botica_id NOT IN (SELECT id FROM demo)
UNION ALL SELECT 'movimientos_stock', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM movimientos_stock t WHERE t.botica_id NOT IN (SELECT id FROM demo)
UNION ALL SELECT 'notificaciones', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM notificaciones t WHERE t.botica_id NOT IN (SELECT id FROM demo)
UNION ALL SELECT 'clientes', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM clientes t
UNION ALL SELECT 'comprobantes_electronicos', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM comprobantes_electronicos t
UNION ALL SELECT 'reportes_digemid', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM reportes_digemid t
UNION ALL SELECT 'lotes_fraccionados', count(*), md5(COALESCE(string_agg(t::text, '|' ORDER BY t.id), '')) FROM lotes_fraccionados t;
