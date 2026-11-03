import { test, expect, Page } from '@playwright/test';
import { login } from './helpers';

/**
 * Móvil y tablet: a 360x800 ninguna pantalla puede ser más ancha que la
 * pantalla (antes, el texto del encabezado la llevaba a 429 px y se
 * cortaban el precio, "Tarjeta" y el TOTAL). Recorre cada pantalla con
 * los dos roles. Solo lectura: no cobra ni guarda nada.
 */
test.use({ viewport: { width: 360, height: 800 }, hasTouch: true });

const TECNICO = { usuario: 'demo_carmen_luis', password: 'demo123' } as const;
const ADMIN = { usuario: 'demo_carmen_patricia', password: 'demo123' } as const;

const PANTALLAS_TECNICO = [
  '/alertas',
  '/punto-venta',
  '/ventas',
  '/inventario',
  '/merma',
  '/caja',
  '/caja?tab=cierre',
];
const PANTALLAS_ADMIN = [...PANTALLAS_TECNICO, '/catalogo', '/kardex', '/usuarios', '/reportes'];

async function sinDesborde(page: Page, donde: string): Promise<void> {
  await page.waitForLoadState('networkidle');
  const m = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(
    m.scrollWidth,
    `${donde}: la página mide ${m.scrollWidth} px en una pantalla de ${m.innerWidth}`,
  ).toBeLessThanOrEqual(m.innerWidth);
}

/** Un modal recorta lo que se sale (scrollWidth no lo ve): ningún campo puede pasar su borde derecho. */
async function sinCortesEnModal(page: Page, donde: string): Promise<void> {
  const cortados = await page
    .locator('.modal-bs')
    .first()
    .evaluate((modal) => {
      const borde = modal.getBoundingClientRect().right;
      return [...modal.querySelectorAll('.bs-field, input, select, button')]
        .filter((el) => {
          const r = el.getBoundingClientRect();
          return r.width > 0 && r.right > borde + 1;
        })
        .map((el) => `${el.tagName.toLowerCase()}${el.id ? '#' + el.id : ''}`);
    });
  expect(cortados, `${donde}: campos cortados por el borde del modal`).toEqual([]);
}

test('técnico: ninguna pantalla desborda a 360 px', async ({ page }) => {
  await login(page, 'Mañana', TECNICO);
  for (const ruta of PANTALLAS_TECNICO) {
    await page.goto(ruta);
    await sinDesborde(page, ruta);
  }
});

test('administrador: ninguna pantalla desborda a 360 px, tampoco los formularios de Catálogo e Inventario', async ({
  page,
}) => {
  await login(page, 'Mañana', ADMIN);
  for (const ruta of PANTALLAS_ADMIN) {
    await page.goto(ruta);
    await sinDesborde(page, ruta);
  }
  await page.goto('/catalogo');
  await page.getByRole('button', { name: 'Nuevo producto' }).first().click();
  await sinDesborde(page, 'Catálogo → Nuevo producto');
  await sinCortesEnModal(page, 'Catálogo → Nuevo producto');
  await page.goto('/inventario');
  await page.getByRole('button', { name: 'Registrar lote' }).first().click();
  await sinDesborde(page, 'Inventario → Registrar lote');
  await sinCortesEnModal(page, 'Inventario → Registrar lote');
});

test('Punto de Venta a 360 px: el carrito plegado es una barra y no tapa los resultados; abierto se ven los nombres', async ({
  page,
}) => {
  await login(page, 'Mañana', TECNICO);
  await page.goto('/punto-venta');

  const barra = page.locator('.carrito-panel__barra');
  await expect(barra).toHaveText(/Carrito vacío/);
  expect((await page.locator('app-carrito-panel').boundingBox())!.height).toBeLessThanOrEqual(80);

  // Un toque normal (por coordenadas) sobre un resultado tiene que llegar: antes el carrito vacío lo tapaba.
  for (const nombre of ['Diclofenaco 50 mg', 'Banditas adhesivas']) {
    await page.locator('#pos-query').fill(nombre);
    await page.locator('.pos__resultado', { hasText: nombre }).first().click();
    await page.locator('.bs-option-row').first().click();
    await expect(page.locator('.presentacion-modal')).toHaveCount(0);
  }
  await expect(barra).toHaveText(/2 líneas · S\/ \d+\.\d{2}/);
  expect((await page.locator('app-carrito-panel').boundingBox())!.height).toBeLessThanOrEqual(80);
  await sinDesborde(page, 'Punto de Venta con carrito plegado');

  // "Cobrar" de la barra abre la hoja en el pago (no cobra) y los nombres de las líneas se ven.
  let posts = 0;
  page.on('request', (r) => {
    if (r.method() === 'POST' && r.url().includes('/api/ventas')) posts++;
  });
  await barra.getByRole('button', { name: 'Cobrar' }).click();
  await expect(page.locator('.carrito-panel__footer')).toBeVisible();
  await expect(page.locator('.carrito-panel__metodo-btn--activo')).toBeFocused();
  for (const nombre of ['Diclofenaco 50 mg', 'Banditas adhesivas']) {
    const linea = page.locator('.carrito-panel__linea-nombre', { hasText: nombre });
    await linea.scrollIntoViewIfNeeded();
    await expect(linea).toBeVisible();
    expect((await linea.boundingBox())!.height).toBeGreaterThan(10);
  }
  await expect(page.locator('.carrito-panel__total-valor')).toBeInViewport();
  await sinDesborde(page, 'Punto de Venta con la hoja abierta');
  expect(posts).toBe(0);

  await page.getByRole('button', { name: /Ocultar/ }).click();
  await expect(barra).toBeVisible();
});

test('encabezado a 360 px: una fila, y una botica sin dirección no muestra "null"', async ({
  page,
}) => {
  // La base no se toca: solo se reescribe la respuesta del login.
  await page.route('**/api/auth/login', async (route) => {
    const r = await route.fetch();
    const cuerpo = await r.json();
    const u = cuerpo.usuario ?? cuerpo;
    u.boticaNombre = 'Charito';
    u.boticaDireccion = null;
    await route.fulfill({ response: r, json: cuerpo });
  });
  await login(page, 'Mañana', TECNICO);

  await expect(page.locator('.app-header__sede')).toHaveText('Charito');
  const alto = (await page.locator('app-header').boundingBox())!.height;
  expect(alto, 'el encabezado ocupa una sola fila').toBeLessThanOrEqual(64);
  await expect(page.getByRole('button', { name: 'Salir' }).filter({ visible: true })).toHaveCount(
    1,
  );
  await sinDesborde(page, 'encabezado sin dirección');
});

test.describe('tablet horizontal (1024x768)', () => {
  test.use({ viewport: { width: 1024, height: 768 }, hasTouch: true });

  test('Punto de Venta a dos columnas: el carrito va a la derecha, sin barra plegada y sin tapar la barra lateral', async ({
    page,
  }) => {
    await login(page, 'Mañana', TECNICO);
    await page.goto('/punto-venta');
    await expect(page.locator('#pos-query')).toBeVisible();

    await expect(page.locator('.carrito-panel__barra')).toBeHidden();
    const carrito = (await page.locator('app-carrito-panel').boundingBox())!;
    const principal = (await page.locator('.pos__main').boundingBox())!;
    const lateral = (await page.locator('app-sidebar-nav').boundingBox())!;
    expect(
      carrito.x,
      'el carrito está a la derecha de la columna principal',
    ).toBeGreaterThanOrEqual(principal.x + principal.width);
    expect(carrito.x, 'el carrito no tapa la barra lateral').toBeGreaterThanOrEqual(
      lateral.x + lateral.width,
    );
    await expect(page.locator('.carrito-panel__footer')).toBeVisible();
    await sinDesborde(page, 'Punto de Venta a 1024 px');
  });
});
