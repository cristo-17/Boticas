import { test, expect } from '@playwright/test';
import { ADMIN, TECNICO, login } from './helpers';

/**
 * Tarea 18, Bloque C: gráfico de 7 días en el Dashboard (solo ADMINISTRADOR),
 * accesos rápidos y agrupación semanal en Resultados, y descarga del .xlsx.
 * Solo lectura: no modifica botica_db.
 */
test('ADMINISTRADOR: Dashboard con gráfico de 7 días → Resultados → semana → Descargar Excel', async ({ page }) => {
  await login(page, 'Tarde', ADMIN);

  // Dashboard: tarjeta de 7 días con el canvas de Chart.js ya dibujado (carga diferida).
  const tarjeta = page.getByTestId('tendencia-7-dias');
  await expect(tarjeta).toBeVisible();
  const canvas = tarjeta.locator('canvas');
  await expect(canvas).toBeVisible();
  await expect.poll(async () => canvas.evaluate((c: HTMLCanvasElement) => c.width)).toBeGreaterThan(0);

  await tarjeta.click();
  await expect(page).toHaveURL(/\/reportes/);

  // Resultados: "Este mes" por defecto, luego "Mes anterior" y agrupar por semana.
  await expect(page.getByRole('heading', { name: 'Reporte de resultados' })).toBeVisible();
  const respuestaSemana = page.waitForResponse(
    (r) => r.url().includes('/api/reportes/resultados?') && r.url().includes('agrupar=semana'),
  );
  await page.getByText('Mes anterior', { exact: true }).click();
  await page.getByText('Semana', { exact: true }).click();
  const json = await (await respuestaSemana).json();
  expect(json.agrupar).toBe('semana');
  expect(json.filas.length).toBeGreaterThanOrEqual(4);
  // La etiqueta de la primera fila viene del servidor ("dd/MM al dd/MM") y es la que se muestra.
  await expect(page.locator('.reportes__tabla tbody tr').first()).toContainText(json.filas[0].etiqueta);
  await expect(page.locator('app-grafico-resultados canvas')).toBeVisible();

  const descarga = page.waitForEvent('download');
  await page.getByRole('button', { name: /Descargar Excel/ }).click();
  const archivo = await descarga;
  expect(archivo.suggestedFilename()).toBe(`reporte-resultados_${json.desde}_${json.hasta}.xlsx`);
});

test('TECNICO: el Dashboard no muestra el gráfico ni pide el reporte (conteo ciego)', async ({ page }) => {
  const pedidos: string[] = [];
  page.on('request', (r) => {
    if (r.url().includes('/api/reportes')) pedidos.push(r.url());
  });
  await login(page, 'Tarde', TECNICO);
  await expect(page.getByText('Boletas del día')).toBeVisible();
  await expect(page.getByTestId('tendencia-7-dias')).toHaveCount(0);
  expect(pedidos).toEqual([]);
});
