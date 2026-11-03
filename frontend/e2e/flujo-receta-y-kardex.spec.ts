import { test, expect, Page, APIRequestContext } from '@playwright/test';
import { login, asegurarCajaAbierta, ADMIN, TECNICO } from './helpers';

const API = 'http://localhost:8080/api';

/**
 * Tarea 20 de punta a punta, sin mocks:
 * 1. El ADMINISTRADOR crea en Catálogo un producto marcado "Se vende solo con receta".
 * 2. Se le registra un lote (por la API: el alta de lote ya tiene su propio flujo).
 * 3. El TECNICO lo vende: al cobrar, Punto de Venta pide la receta y no deja
 *    seguir sin número ni CMP.
 * 4. El ADMINISTRADOR abre su Kardex: ENTRADA del lote, SALIDA con receta con
 *    su número, y el saldo coincide con el stock de los lotes.
 */
test('producto con receta: se vende con la receta y el Kardex lo registra como salida con receta', async ({ browser, request }) => {
  const sufijo = Date.now().toString().slice(-8);
  const nombre = `Amoxicilina E2E ${sufijo}`;
  const codigo = `RXE2E${sufijo}`;

  // --- 1. Administrador: alta en Catálogo con "requiere receta" ---
  const admin = await (await browser.newContext()).newPage();
  await login(admin, 'Tarde', ADMIN);
  await admin.goto('/catalogo');
  await admin.getByRole('button', { name: 'Nuevo producto' }).click();
  await admin.locator('#cat-nombre').fill(nombre);
  await admin.locator('#cat-categoria').fill('Antibióticos');
  await admin.locator('#cat-codigo').fill(codigo);
  await admin.locator('#cat-unidad').fill('cápsula');
  await admin.locator('#cat-receta').check();
  await admin.locator('.catalogo__pres-precio').first().fill('2.50');
  await admin.getByRole('button', { name: 'Guardar producto' }).click();
  await admin.getByLabel('Buscar en el catálogo').fill(codigo);
  const fila = admin.locator('tr', { hasText: nombre });
  await expect(fila).toBeVisible();
  await expect(fila.getByText('Con receta')).toBeVisible();

  // --- 2. Lote de 6 unidades, por la API ---
  const productoId = await registrarLote(request, codigo, 6);

  // --- 3. Técnico: vende 2 con receta ---
  const tecnico = await (await browser.newContext()).newPage();
  await login(tecnico, 'Tarde', TECNICO);
  await cerrarCajaPropiaSiQuedoAbierta(tecnico);
  await asegurarCajaAbierta(tecnico, '100.00');

  await tecnico.goto('/punto-venta');
  await tecnico.locator('#pos-query').fill(codigo);
  const resultado = tecnico.locator('.pos__resultado', { hasText: nombre });
  await expect(resultado).toBeVisible();
  await expect(resultado.getByText('Con receta')).toBeVisible();
  await resultado.click();
  await tecnico.locator('.bs-option-row', { hasText: 'Unidad' }).first().click();
  await tecnico.locator('.carrito-panel__qty-btn[aria-label="Más"]').first().click();
  await expect(tecnico.locator('.carrito-panel__linea', { hasText: nombre }).getByText('Con receta')).toBeVisible();

  const botonCobrar = tecnico.getByRole('button', { name: 'Cobrar S/ 5.00' });
  await botonCobrar.click();
  // Sin datos no cobra: el modal explica qué falta y no sale ningún POST.
  await expect(tecnico.getByText('Datos de la receta')).toBeVisible();
  await expect(tecnico.getByText(`Necesitan receta: ${nombre}`)).toBeVisible();
  await tecnico.getByRole('button', { name: 'Cobrar con receta' }).click();
  await expect(tecnico.getByText('Falta el número de receta.')).toBeVisible();

  await tecnico.locator('#receta-numero').fill(`RX-${sufijo}`);
  await tecnico.locator('#receta-cmp').fill('54321');
  await tecnico.locator('#receta-medico').fill('Dra. Ana Torres');
  const venta = tecnico.waitForResponse((r) => r.url().endsWith('/api/ventas') && r.request().method() === 'POST');
  await tecnico.getByRole('button', { name: 'Cobrar con receta' }).click();
  const cuerpoVenta = await (await venta).json();
  expect(cuerpoVenta.receta.numero).toBe(`RX-${sufijo}`);
  await expect(tecnico.getByText(/Venta cobrada/)).toBeVisible();
  await expect(tecnico.getByText('Datos de la receta')).toHaveCount(0);

  // Limpieza: el técnico cierra su caja cuadrada (apertura + esta venta).
  await tecnico.goto('/caja?tab=cierre');
  await tecnico.locator('#cierre-contado').fill('105.00');
  await tecnico.getByRole('button', { name: 'Cerrar caja' }).click();
  await tecnico.getByRole('button', { name: 'Sí, cerrar caja' }).click();
  await expect(tecnico.getByText('Caja cuadrada', { exact: true })).toBeVisible();

  // --- 4. Administrador: Kardex del producto ---
  await admin.goto(`/kardex?productoId=${productoId}`);
  await expect(admin.getByRole('heading', { name: nombre })).toBeVisible();
  const tabla = admin.locator('table');
  await expect(tabla.getByText('Entrada (lote)')).toBeVisible();
  await expect(tabla.getByText('Salida con receta')).toBeVisible();
  await expect(tabla.getByText(`Receta RX-${sufijo}`)).toBeVisible();
  await expect(admin.getByText('El saldo del Kardex coincide con el stock de los lotes.')).toBeVisible();
  await expect(tabla.locator('tbody tr').last().locator('.kardex__saldo')).toHaveText('4');

  // Excel del Kardex, generado por el servidor.
  const descarga = admin.waitForEvent('download');
  await admin.getByRole('button', { name: /Descargar Excel/ }).click();
  expect((await descarga).suggestedFilename()).toBe(`kardex_${productoId}.xlsx`);
});

/** El alta de lote ya tiene su propio flujo; aquí basta la API, con el token del administrador. */
async function registrarLote(request: APIRequestContext, codigo: string, stock: number): Promise<number> {
  const login = await request.post(`${API}/auth/login`, { data: { ...ADMIN, turno: 'Tarde' } });
  const token = (await login.json()).token as string;
  const headers = { Authorization: `Bearer ${token}` };
  const productos = await (await request.get(`${API}/productos/codigo/${codigo}`, { headers })).json();
  const lote = await request.post(`${API}/lotes`, {
    headers,
    data: { productoId: productos.id, codigo: `L-${codigo}`, fechaVencimiento: '2030-01-01', stock, costoUnitario: 1.0 },
  });
  expect(lote.status()).toBe(200);
  return productos.id as number;
}

async function cerrarCajaPropiaSiQuedoAbierta(page: Page): Promise<void> {
  const hoy = page.waitForResponse((r) => r.url().endsWith('/api/caja/hoy') && r.request().method() === 'GET');
  await page.goto('/caja?tab=cierre');
  const cuerpo = await (await hoy).text();
  if (cuerpo && JSON.parse(cuerpo)?.abierta) {
    await page.locator('#cierre-contado').fill('0');
    await page.getByRole('button', { name: 'Cerrar caja' }).click();
    await page.getByRole('button', { name: 'Sí, cerrar caja' }).click();
    await expect(page.locator('app-resultado-cierre .eyebrow', { hasText: 'Caja cerrada' })).toBeVisible();
  }
}
