import { Page, expect } from '@playwright/test';

export const TECNICO = { usuario: 'rosa.quispe', password: 'tecnico123' } as const;
// ADMINISTRADOR: POST /api/caja/cerrar exige este rol (SecurityConfig, Tarea 12) -- un TECNICO
// puede abrir caja y vender, pero no cerrarla.
export const ADMIN = { usuario: 'carlos.mendoza', password: 'admin123' } as const;

/**
 * Login real contra el backend -- sin mocks. turno se elige libremente en
 * el login (Hueco 7: nunca viene de la tabla usuarios). Cada flujo usa un
 * usuario propio (no solo un turno propio): CajaService.obtenerCajaDeHoy()
 * busca la caja abierta del USUARIO sin filtrar por turno, así que dos
 * flujos que compartieran usuario (aunque con turnos distintos) chocarían
 * si uno deja una caja abierta sin cerrar.
 */
export async function login(
  page: Page,
  turno: 'Mañana' | 'Tarde' | 'Noche' = 'Tarde',
  credenciales: { usuario: string; password: string } = TECNICO,
) {
  await page.goto('/login');
  await page.getByLabel('Usuario').fill(credenciales.usuario);
  await page.getByLabel('Contraseña').fill(credenciales.password);
  // No usar getByRole('button', {name: turno}) acá: el botón de submit dice
  // "Ingresar al turno tarde" y también matchea por substring -- .login__turno acota al selector de turno.
  await page.locator('.login__turno', { hasText: turno }).click();
  await page.getByRole('button', { name: /Ingresar al turno/ }).click();
  await expect(page).toHaveURL(/\/alertas/);
}

/** Abre caja si no hay una ya abierta hoy para este turno -- idempotente, para que cada flujo no dependa del orden de ejecución. */
export async function asegurarCajaAbierta(page: Page, montoInicial = '100.00') {
  await page.goto('/caja');
  const bloqueada = page.getByText('Ya existe una caja abierta hoy');
  if (await bloqueada.isVisible().catch(() => false)) {
    return;
  }
  await page.locator('#apertura-monto').fill(montoInicial);
  await page.getByRole('button', { name: 'Abrir caja' }).click();
  await expect(page.getByText('Ya existe una caja abierta hoy')).toBeVisible();
}
