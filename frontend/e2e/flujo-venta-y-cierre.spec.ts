import { test, expect } from '@playwright/test';
import { login, ADMIN } from './helpers';

/**
 * Flujo 1 (pedido explícito del usuario): login -> abrir caja -> venta
 * -> cerrar caja cuadrada. Usa ADMIN (carlos.mendoza): cerrar caja es
 * hasRole('ADMINISTRADOR') (SecurityConfig, Tarea 12) -- un TECNICO no
 * puede completar este flujo. Usuario propio (no compartido con los
 * otros 2 flujos) para que ninguna caja abierta de otro flujo interfiera
 * con obtenerCajaDeHoy(), que busca por usuario sin filtrar turno.
 */
test('login, abrir caja, cobrar una venta y cerrar la caja cuadrada', async ({ page }) => {
  await login(page, 'Tarde', ADMIN);

  // --- Abrir caja ---
  await page.goto('/caja');
  await expect(page.getByRole('heading', { name: 'Apertura de caja' })).toBeVisible();
  await page.locator('#apertura-monto').fill('100.00');
  await page.getByRole('button', { name: 'Abrir caja' }).click();
  await expect(page.getByText('Ya existe una caja abierta hoy')).toBeVisible();

  // --- Venta: buscar un producto real del seed y cobrarlo ---
  await page.goto('/punto-venta');
  await page.locator('#pos-query').fill('Paracetamol');
  const resultado = page.locator('.pos__resultado', { hasText: 'Paracetamol' }).first();
  await expect(resultado).toBeVisible();
  await resultado.click();

  // Presentación "Unidad" (factor 1): el total es exactamente precio x 1, sin conversión que calcular a mano.
  await page.locator('.bs-option-row', { hasText: 'Unidad' }).first().click();

  const botonCobrar = page.getByRole('button', { name: /Cobrar S\/ [\d.]+/ });
  await expect(botonCobrar).toBeVisible();
  const textoCobrar = await botonCobrar.innerText();
  const totalVenta = parseFloat(textoCobrar.match(/Cobrar S\/ ([\d.]+)/)![1]);
  expect(totalVenta).toBeGreaterThan(0);

  await botonCobrar.click();
  await expect(page.getByText(/Venta cobrada|Venta guardada/)).toBeVisible();

  // --- Cerrar caja: monto contado exacto (apertura + venta) => Caja cuadrada ---
  await page.goto('/caja');
  await page.getByRole('button', { name: 'Cierre', exact: true }).click();
  const montoEsperado = (100 + totalVenta).toFixed(2);
  await page.locator('#cierre-contado').fill(montoEsperado);
  await page.getByRole('button', { name: 'Cerrar caja' }).click();
  await page.getByRole('button', { name: 'Sí, cerrar caja' }).click();

  await expect(page.getByText('Caja cuadrada', { exact: true })).toBeVisible();
  await expect(page.getByText('El conteo coincide con lo esperado.')).toBeVisible();
});
