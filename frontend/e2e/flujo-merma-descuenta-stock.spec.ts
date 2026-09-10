import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * Flujo 2 (pedido explícito del usuario): registrar merma -> verificar
 * que el stock bajó en Inventario. Es destructiva y sin deshacer
 * (CLAUDE.md) -- se corre contra botica_db recién reconstruida
 * (flyway:clean/migrate), nunca contra producción.
 *
 * No requiere caja abierta (MermaScreen no depende de CajaService).
 */
test('registrar una merma descuenta el stock del lote, verificado en Inventario', async ({ page }) => {
  await login(page, 'Mañana');

  // --- Elegir el producto y el primer lote (FEFO), leer su stock ANTES de la merma ---
  await page.goto('/merma');
  await page.locator('#merma-producto-query').fill('Paracetamol');
  const resultado = page.locator('.merma__resultado', { hasText: 'Paracetamol' }).first();
  await expect(resultado).toBeVisible();
  await resultado.click();

  const loteSelect = page.locator('#merma-lote');
  await expect(loteSelect).toBeVisible();
  const codigoLote = await loteSelect.locator('option:checked').innerText();

  const loteInfo = page.locator('.merma__lote-info');
  const textoAntes = await loteInfo.innerText();
  const stockAntes = parseInt(textoAntes.match(/(\d+) unidades en stock/)![1], 10);
  expect(stockAntes).toBeGreaterThan(0); // si fuera 0 no habría nada que dar de baja -- el seed determinista garantiza que el primer lote FEFO tiene stock

  // --- Registrar la merma ---
  await page.locator('.merma__stepper-input').fill('1');
  // "Vencimiento" no exige observación (a diferencia de "Otro"/"Robo o pérdida") -- ver CLAUDE.md/BITACORA [Bloque C].
  await page.getByRole('button', { name: 'Vencimiento', exact: true }).click();
  await page.getByRole('button', { name: 'Registrar merma' }).click();
  await page.getByRole('button', { name: 'Sí, dar de baja' }).click();
  await expect(page.getByText('Merma registrada')).toBeVisible();

  // --- Verificar en Inventario (pantalla y consulta al servidor distintas de Merma) que el stock bajó exactamente en 1 ---
  await page.goto('/inventario');
  const filaLote = page.locator('tr', { has: page.locator('.inventario__code', { hasText: codigoLote }) }).first();
  await expect(filaLote).toBeVisible();
  const stockDespuesTexto = await filaLote.locator('.inventario__stock').innerText();
  const stockDespues = parseInt(stockDespuesTexto.trim(), 10);

  expect(stockDespues).toBe(stockAntes - 1);
});
