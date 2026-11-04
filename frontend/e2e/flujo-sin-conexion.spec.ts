import { test, expect, Page, APIRequestContext } from '@playwright/test';
import { login } from './helpers';

/**
 * Tarea 22 (docs/DISENO-OFFLINE.md): vender sin conexión y verificar EN EL
 * SERVIDOR que hay UNA sola venta con esa clave. Dos casos:
 * 1. context.setOffline(true): se cobra sin red, queda en la cola, y al volver
 *    la conexión se envía sola.
 * 2. La red se corta a la mitad del envío: el servidor registró la venta pero
 *    la respuesta se pierde. La venta queda en la cola con la misma clave y el
 *    reenvío devuelve la misma venta (idempotencia), no una segunda.
 * 3. (PWA) La caja se abrió en otro equipo y el cajero va directo a Punto de
 *    Venta, sin pasar por Caja: igual vende sin conexión (el Shell consulta la
 *    caja abierta al iniciar la sesión). Una recarga con la venta todavía en la
 *    cola no la pierde.
 * Usa técnicos demo con su propia caja (no chocan con los otros flujos).
 */
const TECNICO_DEMO = { usuario: 'demo_esperanza_andres', password: 'demo123' } as const;
const API = process.env['E2E_API_URL'] ?? 'http://localhost:8080/api';
const TECNICO_OTRO_EQUIPO = { usuario: 'demo_sanmartin_karina', password: 'demo123' } as const;
const TECNICO_SIN_CAJA = { usuario: 'demo_carmen_miguel', password: 'demo123' } as const;
const PRODUCTO = 'Cetirizina 10 mg';

test.describe.configure({ mode: 'serial' });

test('sin conexión: se cobra, queda en la cola y al volver la señal llega UNA sola venta', async ({ page, context, request }) => {
  const enviadas = escucharPostVentas(page);
  await prepararPuntoDeVenta(page);

  await context.setOffline(true);
  await expect(page.getByTestId('pos-sin-conexion')).toBeVisible();

  await agregarAlCarrito(page);
  await page.getByRole('button', { name: /Cobrar S\/ [\d.]+/ }).click();
  await expect(page.getByText('Venta guardada sin conexión. Se enviará sola.')).toBeVisible();
  await expect(page.getByTestId('contador-cola')).toHaveText('1');
  expect(enviadas, 'sin red no sale ningún POST que llegue al servidor').toHaveLength(0);

  // El panel muestra la venta sin montos (conteo ciego).
  await page.getByTestId('indicador-conexion').click();
  await expect(page.getByTestId('fila-cola')).toContainText(PRODUCTO);
  await expect(page.getByTestId('fila-cola')).not.toContainText('S/');

  await context.setOffline(false); // evento "online" → comprobación → se vacía la cola
  await expect(page.getByTestId('contador-cola')).toHaveCount(0, { timeout: 30_000 });

  expect(enviadas.length).toBeGreaterThanOrEqual(1);
  const clave = enviadas[0].claveIdempotencia;
  expect(enviadas[0].sinConexion?.cajaId).toBeGreaterThan(0);
  const ventas = await ventasConClave(request, page, clave);
  expect(ventas, 'una sola venta con esa clave en el servidor').toHaveLength(1);
  expect(ventas[0].vendidaSinConexion).toBe(true);
  expect(ventas[0].horaDispositivo).toBeTruthy();
});

test('la red se corta a la mitad del envío: el reenvío con la misma clave no duplica', async ({ page, request }) => {
  const enviadas = escucharPostVentas(page);
  await prepararPuntoDeVenta(page);

  // El primer POST llega al servidor (se registra) y la respuesta se pierde en el camino.
  let cortado = false;
  await page.route('**/api/ventas', async (ruta) => {
    if (ruta.request().method() === 'POST' && !cortado) {
      cortado = true;
      await ruta.fetch();
      await ruta.abort('connectionreset');
      return;
    }
    await ruta.continue();
  });

  await agregarAlCarrito(page);
  await page.getByRole('button', { name: /Cobrar S\/ [\d.]+/ }).click();
  await expect(page.getByText('Venta guardada sin conexión. Se enviará sola.')).toBeVisible();
  await expect(page.getByTestId('contador-cola')).toHaveText('1');

  // "Enviar ahora": comprueba la conexión y reenvía con la MISMA clave.
  await page.getByTestId('indicador-conexion').click();
  await page.getByRole('button', { name: 'Enviar ahora' }).click();
  await expect(page.getByTestId('contador-cola')).toHaveCount(0, { timeout: 30_000 });

  expect(enviadas.length).toBe(2);
  expect(enviadas[1].claveIdempotencia).toBe(enviadas[0].claveIdempotencia);
  const ventas = await ventasConClave(request, page, enviadas[0].claveIdempotencia);
  expect(ventas, 'el reenvío devolvió la venta ya registrada, no una segunda').toHaveLength(1);
  // La que quedó es la del primer envío (en línea): el reenvío solo la devolvió.
  expect(ventas[0].vendidaSinConexion).toBe(false);
});

test('caja abierta en otro equipo: directo a Punto de Venta, vende sin conexión y una recarga no pierde la cola', async ({ page, context, request }) => {
  // La caja se abre por la API (como desde otro equipo): este navegador nunca la vio en la pantalla Caja.
  const token = await tokenDe(request, TECNICO_OTRO_EQUIPO);
  const apertura = await request.post(`${API}/caja/abrir`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { montoInicial: 50, turno: 'Tarde' },
  });
  expect([200, 409]).toContain(apertura.status()); // 409: ya estaba abierta desde otra corrida

  const foto = page.waitForResponse((r) => r.url().includes('/api/productos/catalogo-offline') && r.ok());
  const cajaHoy = page.waitForResponse((r) => r.url().endsWith('/api/caja/hoy') && r.ok()).catch(() => null);
  await login(page, 'Tarde', TECNICO_OTRO_EQUIPO);
  await foto;
  // Sin esperarla a la fuerza: si la app no la pidiera, el test tiene que fallar en la venta, no acá.
  await Promise.race([cajaHoy, page.waitForTimeout(5_000)]);
  await page.goto('/punto-venta');

  await context.setOffline(true);
  await expect(page.getByTestId('pos-sin-conexion')).toBeVisible();
  await agregarAlCarrito(page);
  await page.getByRole('button', { name: /Cobrar S\/ [\d.]+/ }).click();
  await expect(page.getByText('Venta guardada sin conexión. Se enviará sola.')).toBeVisible();
  await expect(page.getByTestId('contador-cola')).toHaveText('1');

  // Vuelve la red pero el servidor no contesta las ventas: la venta sigue en la cola y se recarga la app.
  await page.route('**/api/ventas', (ruta) =>
    ruta.request().method() === 'POST' ? ruta.abort('internetdisconnected') : ruta.continue(),
  );
  await context.setOffline(false);
  await page.reload();
  await expect(page.getByTestId('contador-cola')).toHaveText('1'); // la recarga no la perdió (IndexedDB)

  const enviadas = escucharPostVentas(page);
  await page.unroute('**/api/ventas');
  // La cola reintenta sola (al reconectar o cada minuto); "Enviar ahora" solo si todavía no salió.
  const enviarAhora = page.getByRole('button', { name: 'Enviar ahora' });
  await page.getByTestId('indicador-conexion').click();
  if (await enviarAhora.isVisible()) await enviarAhora.click();
  await expect(page.getByTestId('contador-cola')).toHaveCount(0, { timeout: 70_000 });
  const ventas = await ventasConClave(request, page, enviadas[0].claveIdempotencia);
  expect(ventas, 'una sola venta con esa clave en el servidor').toHaveLength(1);
  expect(ventas[0].vendidaSinConexion).toBe(true);
});

test('sin caja abierta y sin conexión: Cobrar avisa y no pierde el carrito; "Abrir caja" falla al instante', async ({ page, context }) => {
  const foto = page.waitForResponse((r) => r.url().includes('/api/productos/catalogo-offline') && r.ok());
  await login(page, 'Tarde', TECNICO_SIN_CAJA);
  await foto;
  // El build de los E2E no tiene service worker: la pantalla Caja (diferida) se carga una vez con red.
  await page.goto('/caja');
  await expect(page.locator('#apertura-monto')).toBeVisible();
  await page.locator('a[href="/punto-venta"]').first().click();
  await agregarAlCarrito(page);

  await context.setOffline(true);
  await expect(page.getByTestId('pos-sin-conexion')).toContainText('sin caja abierta en este equipo');
  const enviadas = escucharPostVentas(page);
  let navegaciones = 0;
  page.on('framenavigated', (f) => {
    if (f === page.mainFrame()) navegaciones++;
  });
  await page.getByRole('button', { name: /Cobrar S\/ [\d.]+/ }).click();
  await expect(page.getByTestId('error-cobro')).toContainText('Abre la caja con conexión antes de vender sin conexión.');
  await expect(page.locator('.carrito-panel__qty-num')).toHaveText('1'); // el carrito sigue
  expect(navegaciones, 'la pantalla no se recarga').toBe(0);
  expect(enviadas, 'sin caja no sale ninguna venta').toHaveLength(0);

  // "Abrir caja" sin red: falla al instante, con un mensaje, y el botón no queda cargando.
  await page.locator('a[href="/caja"]').first().click(); // navegación de la app: sin red no se recarga la página
  await page.locator('#apertura-monto').fill('50.00');
  const inicio = Date.now();
  await page.getByRole('button', { name: 'Abrir caja' }).click();
  await expect(page.getByText('Esta acción necesita conexión.').first()).toBeVisible({ timeout: 3_000 });
  expect(Date.now() - inicio).toBeLessThan(3_000);
  await expect(page.getByRole('button', { name: 'Abrir caja' })).toBeEnabled();
  await context.setOffline(false);
});

async function tokenDe(request: APIRequestContext, cred: { usuario: string; password: string }): Promise<string> {
  const r = await request.post(`${API}/auth/login`, { data: { ...cred, turno: 'Tarde' } });
  expect(r.ok()).toBe(true);
  return ((await r.json()) as { token: string }).token;
}

interface CuerpoVenta {
  claveIdempotencia: string;
  sinConexion?: { cajaId: number; horaDispositivo: string };
}

function escucharPostVentas(page: Page): CuerpoVenta[] {
  const cuerpos: CuerpoVenta[] = [];
  page.on('request', (r) => {
    if (r.method() === 'POST' && r.url().endsWith('/api/ventas')) cuerpos.push(r.postDataJSON() as CuerpoVenta);
  });
  return cuerpos;
}

async function prepararPuntoDeVenta(page: Page): Promise<void> {
  // Desde la PWA el catálogo se baja al iniciar la sesión (Shell), no al entrar a Punto de Venta.
  const foto = page.waitForResponse((r) => r.url().includes('/api/productos/catalogo-offline') && r.ok());
  await login(page, 'Tarde', TECNICO_DEMO);
  await foto; // la foto del catálogo ya está en el dispositivo
  await cajaAbierta(page);
  await page.goto('/punto-venta');
}

/** Como asegurarCajaAbierta, pero esperando a que la pantalla termine de cargar: el segundo test ya encuentra la caja abierta. */
async function cajaAbierta(page: Page): Promise<void> {
  await page.goto('/caja');
  const bloqueada = page.getByText('Ya existe una caja abierta hoy');
  const monto = page.locator('#apertura-monto');
  await expect(bloqueada.or(monto)).toBeVisible();
  if (await bloqueada.isVisible()) return;
  await monto.fill('100.00');
  await page.getByRole('button', { name: 'Abrir caja' }).click();
  await expect(bloqueada).toBeVisible();
}

async function agregarAlCarrito(page: Page): Promise<void> {
  await page.locator('#pos-query').fill('Cetirizina');
  await page.locator('.pos__resultado', { hasText: PRODUCTO }).first().click();
  await page.locator('.bs-option-row', { hasText: 'Unidad' }).first().click();
  await expect(page.locator('.carrito-panel__qty-num').first()).toHaveText('1');
}

/** Consulta al servidor (fuera del navegador) las ventas de la caja del técnico con esa clave. */
async function ventasConClave(
  request: APIRequestContext,
  page: Page,
  clave: string,
): Promise<{ claveIdempotencia: string; vendidaSinConexion: boolean; horaDispositivo: string | null }[]> {
  const token = await page.evaluate(() => localStorage.getItem('boticasys_token'));
  const r = await request.get(`${API}/ventas?pagina=0&tamano=100&orden=fecha,desc`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(r.ok()).toBe(true);
  const pagina = (await r.json()) as { contenido: { claveIdempotencia: string; vendidaSinConexion: boolean; horaDispositivo: string | null }[] };
  return pagina.contenido.filter((v) => v.claveIdempotencia === clave);
}
