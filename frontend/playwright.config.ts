import { defineConfig, devices } from '@playwright/test';
import { RUTA_VIDEO_CAMARA } from './e2e/camara-falsa';

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
  globalSetup: './e2e/global-setup.ts', // escribe el .y4m de la cámara falsa (Tarea 19)
  use: {
    // E2E_BASE_URL: otro puerto si el 4200 está ocupado por un ng serve que no es el de esta corrida.
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      testIgnore: /flujo-camara\.spec\.ts/,
    },
    {
      // Tarea 19: cámara falsa que muestra un EAN-13 (e2e/camara-falsa.ts); permiso concedido sin diálogo.
      name: 'camara',
      testMatch: /flujo-camara\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        permissions: ['camera'],
        launchOptions: {
          args: [
            '--use-fake-device-for-media-stream',
            '--use-fake-ui-for-media-stream',
            `--use-file-for-fake-video-capture=${RUTA_VIDEO_CAMARA}`,
          ],
        },
      },
    },
  ],
});
