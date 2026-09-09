import { defineConfig, devices } from '@playwright/test';

/**
 * E2E contra la app real (backend Spring Boot dev + Postgres botica_db,
 * frontend `ng serve`) — no hay mocks acá, a propósito: es lo único que
 * prueba los 3 flujos de punta a punta como los usaría el cajero.
 * webServer queda comentado a propósito (Tarea 13): requiere backend Y
 * frontend corriendo, y el backend necesita botica_db ya migrada/seedeada
 * -- levantar ambos server queda en manos de quien corre la suite, no de
 * Playwright (ver docs/PLAN-DE-PRUEBAS.md, sección E2E).
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false, // los 3 flujos comparten botica_db real -- una caja por usuario/turno/día no tolera dos workers pisándose
  workers: 1,
  retries: 0,
  timeout: 60_000,
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
