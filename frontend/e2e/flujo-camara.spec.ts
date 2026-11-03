import { test, expect, Page } from '@playwright/test';
import { login } from './helpers';
import { CODIGO_CAMARA } from './camara-falsa';

/**
 * Tarea 19 con una cámara falsa de Chromium que muestra un EAN-13 demo
 * (proyecto "camara" de playwright.config.ts). Chromium de Windows y Linux
 * no trae BarcodeDetector, así que esto ejercita el respaldo: el chunk del
 * ponyfill y el .wasm servido desde nuestro dominio. La cámara trasera, la
 * luz física y el permiso del sistema se prueban a mano en el celular
 * (PLAN-DE-PRUEBAS, B). No cobra: el POST de la venta se aborta.
 */
const DEMO_TECNICA = { usuario: 'demo_esperanza_sofia', password: 'demo123' } as const;
const NOMBRE = 'Cetirizina 10 mg';

test('la cámara lee el EAN-13: un solo modal, la línea va como ESCANEO y al salir se apagan todas las pistas', async ({
  page,
}) => {
  const wasm: string[] = [];
  page.on('request', (r) => {
    if (r.url().includes('.wasm')) wasm.push(r.url());
  });

  await login(page, 'Mañana', DEMO_TECNICA);
  await page.goto('/punto-venta');
  expect(wasm, 'el .wasm no se descarga antes de abrir la cámara').toEqual([]);

  await page.getByRole('button', { name: 'Abrir cámara' }).click();
  await expect(page.locator('.presentacion-modal__nombre')).toHaveText(NOMBRE, { timeout: 20_000 });
  expect(wasm.length).toBeGreaterThan(0);
  for (const url of wasm) {
    expect(new URL(url).origin, `.wasm desde ${url}`).toBe(new URL(page.url()).origin);
  }
  await expect(page.getByText('Lector ZXing')).toBeVisible();

  // Mientras el modal está abierto la cámara sigue viendo el código: no se abre un segundo modal.
  const pistas = await guardarPistas(page);
  await page.waitForTimeout(1000);
  await expect(page.locator('.presentacion-modal')).toHaveCount(1);
  await page.locator('.bs-option-row').first().click();

  // El código sigue frente a la cámara al cerrar el modal: no se vuelve a agregar solo.
  await page.waitForTimeout(2000);
  await expect(page.locator('.presentacion-modal')).toHaveCount(0);

  expect(await origenesAlCobrar(page)).toEqual(['ESCANEO']);

  // Salir de la pantalla con la cámara abierta: todas las pistas terminan (la luz se apaga).
  expect(await estadoPistas(page, pistas)).toEqual(['live']);
  await page
    .getByRole('link', { name: /Inventario/ })
    .first()
    .click();
  await expect(page).toHaveURL(/\/inventario/);
  expect(await estadoPistas(page, pistas)).toEqual(['ended']);
});

test('cerrar la cámara con el botón apaga todas las pistas y vuelve a ofrecer Abrir cámara', async ({
  page,
}) => {
  await login(page, 'Mañana', DEMO_TECNICA);
  await page.goto('/punto-venta');
  // Sin código a la vista no interesa: se cierra antes de leer.
  await page.route('**/api/productos/codigo/**', (r) => r.abort());
  await page.getByRole('button', { name: 'Abrir cámara' }).click();
  await expect(page.getByRole('button', { name: 'Cerrar cámara' })).toBeVisible();
  const pistas = await guardarPistas(page);

  await page.getByRole('button', { name: 'Cerrar cámara' }).click();
  expect(await estadoPistas(page, pistas)).toEqual(['ended']);
  await expect(page.getByRole('button', { name: 'Abrir cámara' })).toBeVisible();
});

/** Guarda en window las pistas del video actual, para mirarlas después de que la pantalla las suelte. */
async function guardarPistas(page: Page): Promise<string> {
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          !!(document.querySelector('app-lector-camara video') as HTMLVideoElement | null)
            ?.srcObject,
      ),
    )
    .toBe(true);
  return page.evaluate(() => {
    const video = document.querySelector('app-lector-camara video') as HTMLVideoElement;
    (window as unknown as { __pistas: MediaStreamTrack[] }).__pistas = (
      video.srcObject as MediaStream
    ).getTracks();
    return '__pistas';
  });
}

async function estadoPistas(page: Page, clave: string): Promise<string[]> {
  return page.evaluate(
    (k) => [
      ...new Set(
        (window as unknown as Record<string, MediaStreamTrack[]>)[k].map((t) => t.readyState),
      ),
    ],
    clave,
  );
}

async function origenesAlCobrar(page: Page): Promise<string[]> {
  const cuerpo = new Promise<{ items: { origenCaptura: string }[] }>((resolver) =>
    page.route('**/api/ventas', async (route) => {
      if (route.request().method() !== 'POST') return route.fallback();
      resolver(route.request().postDataJSON());
      await route.abort();
    }),
  );
  await page.getByRole('button', { name: /^Cobrar S\// }).click();
  return (await cuerpo).items.map((i) => i.origenCaptura);
}
