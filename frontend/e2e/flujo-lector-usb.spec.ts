import { test, expect, Page } from '@playwright/test';
import { login } from './helpers';
import { CODIGO_CAMARA } from './camara-falsa';

/**
 * DIF-05 (DECISIONES 2026-11-03, "D4 revisada"): un lector USB "teclea" el
 * código a ~5 ms por tecla y termina con Enter; una persona tarda mucho
 * más. Sin lector físico (Bloque A, punto 6), este E2E cubre la prueba
 * manual A11. No cobra: la petición de venta se intercepta y se aborta,
 * solo se lee su origenCaptura.
 */
const DEMO_TECNICA = { usuario: 'demo_esperanza_sofia', password: 'demo123' } as const;
const NOMBRE = 'Cetirizina 10 mg';

test.describe('lector USB en el buscador de Punto de Venta', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, 'Mañana', DEMO_TECNICA);
    await page.goto('/punto-venta');
    await expect(page.locator('#pos-query')).toBeVisible();
  });

  test('ráfaga a 5 ms + Enter: abre el producto sin elegirlo de la lista y la línea va como ESCANEO', async ({
    page,
  }) => {
    await page.locator('#pos-query').click();
    await page.keyboard.type(CODIGO_CAMARA, { delay: 5 });
    await page.keyboard.press('Enter');

    await expect(page.locator('.presentacion-modal__nombre')).toHaveText(NOMBRE);
    await expect(page.locator('#pos-query')).toHaveValue(''); // el lector tecleó en el campo: se limpia
    await page.locator('.bs-option-row').first().click();

    expect(await origenesAlCobrar(page)).toEqual(['ESCANEO']);
  });

  test('el mismo código a 120 ms por tecla + Enter: no abre nada; elegirlo de los resultados es MANUAL', async ({
    page,
  }) => {
    await page.locator('#pos-query').click();
    await page.keyboard.type(CODIGO_CAMARA, { delay: 120 });
    await page.keyboard.press('Enter');

    const resultado = page.locator('.pos__resultado', { hasText: NOMBRE });
    await expect(resultado).toBeVisible();
    await expect(page.locator('.presentacion-modal__nombre')).toHaveCount(0);
    await resultado.click();
    await page.locator('.bs-option-row').first().click();

    expect(await origenesAlCobrar(page)).toEqual(['MANUAL']);
  });
});

/** Aprieta Cobrar y devuelve el origenCaptura de cada línea del POST /api/ventas, que se aborta (no se cobra nada). */
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
