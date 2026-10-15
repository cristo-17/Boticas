import { test, expect, Page } from '@playwright/test';
import { login, asegurarCajaAbierta, ADMIN, TECNICO } from './helpers';

const NOMBRE_TECNICO = 'Rosa Quispe';

/**
 * Flujo 1 con D-E2-1 (T16): el TECNICO abre su caja y vende; el
 * ADMINISTRADOR, en otra sesión, elige ESA caja entre las abiertas de la
 * botica y la cierra con conteo ciego. Es el flujo real de la botica (y
 * el que nunca funcionó: antes el admin solo podía cerrar su propia caja).
 * Dos contextos de navegador = dos sesiones simultáneas, cada una con su token.
 */
test('el técnico abre y vende; el administrador cierra esa caja cuadrada', async ({ browser }) => {
  const tecnico = await (await browser.newContext()).newPage();
  const admin = await (await browser.newContext()).newPage();

  // --- Limpieza: otros flujos usan al mismo técnico y pueden dejarle una caja
  // abierta con movimientos; el esperado de este test tiene que ser exactamente apertura + venta.
  await login(admin, 'Tarde', ADMIN);
  await irACierre(admin);
  if (await cajaDe(admin).isVisible().catch(() => false)) {
    await cerrarCajaDe(admin, '0');
  }

  // --- Técnico: abre caja y vende ---
  await login(tecnico, 'Tarde', TECNICO);
  await asegurarCajaAbierta(tecnico, '100.00');

  const totalVenta = await venderUnParacetamol(tecnico);

  // --- Administrador: elige la caja del técnico y la cierra a ciegas ---
  await irACierre(admin);
  await expect(cajaDe(admin)).toBeVisible();
  // Conteo ciego: nada del esperado en pantalla antes del POST.
  await expect(admin.getByText('Efectivo esperado')).toHaveCount(0);
  await cerrarCajaDe(admin, (100 + totalVenta).toFixed(2));

  await expect(admin.getByText('Caja cuadrada', { exact: true })).toBeVisible();
  await expect(admin.getByText('El conteo coincide con lo esperado.')).toBeVisible();
  await expect(admin.getByText(`Caja cerrada · ${NOMBRE_TECNICO}`)).toBeVisible();
  await expect(cajaDe(admin)).toHaveCount(0);

  // --- El técnico ya no tiene caja abierta: puede abrir otra ---
  await tecnico.goto('/caja');
  await expect(tecnico.getByRole('heading', { name: 'Apertura de caja' })).toBeVisible();
  await expect(tecnico.getByText('Ya existe una caja abierta hoy')).toHaveCount(0);
});

async function irACierre(admin: Page): Promise<void> {
  await admin.goto('/caja?tab=cierre');
  await expect(admin.getByRole('heading', { name: 'Cajas abiertas de la botica' })).toBeVisible();
}

function cajaDe(admin: Page) {
  return admin.locator('.cierre-admin__caja', { hasText: NOMBRE_TECNICO });
}

async function cerrarCajaDe(admin: Page, contado: string): Promise<void> {
  await cajaDe(admin).click();
  await expect(admin.getByText('Efectivo esperado')).toHaveCount(0);
  await admin.locator('#cierre-contado').fill(contado);
  await admin.getByRole('button', { name: 'Cerrar caja' }).click();
  await admin.getByRole('button', { name: 'Sí, cerrar caja' }).click();
  await expect(admin.getByText(`Caja cerrada · ${NOMBRE_TECNICO}`)).toBeVisible();
}

/**
 * El TECNICO cierra su PROPIA caja (sin cajaId) con conteo ciego. Antes del
 * POST la pantalla no muestra ningún monto de la caja (DECISIONES 2026-10-14):
 * los movimientos salen sin montos. Las únicas cifras permitidas son los
 * umbrales del semáforo (configuración) y el total de ventas digitales
 * (decisión 2026-09-08: no se cuenta en el cajón).
 */
test('el técnico abre, vende y cierra su propia caja sin ver montos antes del cierre', async ({ page }) => {
  await login(page, 'Tarde', TECNICO);

  // --- Limpieza: si quedó una caja abierta de otro flujo, el técnico la cierra.
  // GET (no el preflight OPTIONS); sin caja abierta el cuerpo viene vacío.
  const hoy = page.waitForResponse((r) => r.url().endsWith('/api/caja/hoy') && r.request().method() === 'GET');
  await page.goto('/caja?tab=cierre');
  const cuerpoHoy = await (await hoy).text();
  if (cuerpoHoy && JSON.parse(cuerpoHoy)?.abierta) {
    await cerrarPropia(page, '0');
  }

  await asegurarCajaAbierta(page, '100.00');
  const totalVenta = await venderUnParacetamol(page);

  await page.goto('/caja?tab=cierre');
  await expect(page.locator('#cierre-contado')).toBeVisible();
  await expect(page.getByText(`Venta #`).first()).toBeVisible();
  await expect(page.getByText('Efectivo esperado')).toHaveCount(0);
  // Ningún monto de la caja en pantalla: fuera del semáforo y del total digital, ni una cifra "S/".
  const cifrasDeCaja = await page
    .locator('app-cierre')
    .evaluate((el) => {
      const copia = el.cloneNode(true) as HTMLElement;
      copia.querySelectorAll('.conteo__leyenda-fila, .cierre__esperado, .conteo__monto-box, app-modal').forEach((n) => n.remove());
      return copia.textContent?.match(/S\/\s*[\d.,]+/g) ?? [];
    });
  expect(cifrasDeCaja).toEqual([]);

  await cerrarPropia(page, (100 + totalVenta).toFixed(2));
  await expect(page.getByText('Caja cuadrada', { exact: true })).toBeVisible();
  // Después del POST la lista se muestra completa.
  await expect(page.locator('.cierre__mov-monto').first()).toBeVisible();
});

/** Presentación "Unidad" (factor 1): el total es exactamente precio x 1, sin conversión que calcular a mano. */
async function venderUnParacetamol(page: Page): Promise<number> {
  await page.goto('/punto-venta');
  await page.locator('#pos-query').fill('Paracetamol');
  const resultado = page.locator('.pos__resultado', { hasText: 'Paracetamol' }).first();
  await expect(resultado).toBeVisible();
  await resultado.click();
  await page.locator('.bs-option-row', { hasText: 'Unidad' }).first().click();
  // Con el carrito vacío el botón ya existe como "Cobrar S/ 0.00": esperar a que tenga la línea.
  const botonCobrar = page.getByRole('button', { name: /Cobrar S\/ (?!0\.00)[\d.]+/ });
  await expect(botonCobrar).toBeVisible();
  const total = parseFloat((await botonCobrar.innerText()).match(/Cobrar S\/ ([\d.]+)/)![1]);
  expect(total).toBeGreaterThan(0);
  await botonCobrar.click();
  await expect(page.getByText(/Venta cobrada|Venta guardada/)).toBeVisible();
  return total;
}

async function cerrarPropia(page: Page, contado: string): Promise<void> {
  await page.locator('#cierre-contado').fill(contado);
  await page.getByRole('button', { name: 'Cerrar caja' }).click();
  await page.getByRole('button', { name: 'Sí, cerrar caja' }).click();
  await expect(page.locator('app-resultado-cierre .eyebrow', { hasText: 'Caja cerrada' })).toBeVisible();
}
