import { test, expect } from '@playwright/test';
import { login, asegurarCajaAbierta } from './helpers';

/**
 * Flujo 3 (pedido explícito del usuario): vender más stock del
 * disponible -> se muestra el error correcto. turno 'Noche' -- distinto
 * del 'Tarde' de flujo-venta-y-cierre.spec.ts, para no chocar con la
 * regla de una sola caja abierta por usuario/turno/día.
 *
 * El total disponible se SUMA leyendo cada lote real desde la pantalla
 * de Merma (no se hardcodea un número atado a la fórmula del seed,
 * V4__seed.sql, que podría cambiar) -- después se pide sumaTotal + 1 en
 * Punto de Venta, que el servidor debe rechazar sin importar cuántos
 * lotes tenga el producto ese día.
 */
test('vender más unidades que el stock total disponible muestra el error correcto', async ({ page }) => {
  await login(page, 'Noche');
  await asegurarCajaAbierta(page);

  // --- Sumar el stock de TODOS los lotes de Paracetamol (FEFO consume de todos, no solo del primero) ---
  await page.goto('/merma');
  await page.locator('#merma-producto-query').fill('Paracetamol');
  await page.locator('.merma__resultado', { hasText: 'Paracetamol' }).first().click();

  const loteSelect = page.locator('#merma-lote');
  await expect(loteSelect).toBeVisible();
  const valores = await loteSelect.locator('option').evaluateAll((opts) => opts.map((o) => (o as HTMLOptionElement).value));

  let totalDisponible = 0;
  for (const valor of valores) {
    await loteSelect.selectOption(valor);
    const texto = await page.locator('.merma__lote-info').innerText();
    totalDisponible += parseInt(texto.match(/(\d+) unidades en stock/)![1], 10);
  }
  expect(totalDisponible).toBeGreaterThan(0);

  // --- Intentar vender totalDisponible + 1 unidades del mismo producto ---
  await page.goto('/punto-venta');
  await page.locator('#pos-query').fill('Paracetamol');
  await page.locator('.pos__resultado', { hasText: 'Paracetamol' }).first().click();
  await page.locator('.bs-option-row', { hasText: 'Unidad' }).first().click(); // agrega 1 unidad

  const botonMas = page.getByRole('button', { name: 'Más' });
  for (let i = 0; i < totalDisponible; i++) {
    await botonMas.click();
  }
  await expect(page.locator('.carrito-panel__qty-num')).toHaveText(String(totalDisponible + 1));

  await page.getByRole('button', { name: /Cobrar S\/ [\d.]+/ }).click();

  const banner = page.getByRole('alert');
  await expect(banner).toBeVisible();
  await expect(banner).toContainText('No hay stock suficiente para completar la venta.');

  // No debe haber cobrado nada -- el carrito sigue con la misma cantidad, no se vació.
  await expect(page.locator('.carrito-panel__qty-num')).toHaveText(String(totalDisponible + 1));
});
