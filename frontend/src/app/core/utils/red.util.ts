import { HttpErrorResponse } from '@angular/common/http';

/** Tarea 22 (§2): 502/503/504 = el proxy de Render (o Vercel) sin backend detrás. Cuenta como sin conexión. */
const SIN_BACKEND = new Set([502, 503, 504]);

/**
 * true si la petición no llegó a tener una respuesta del backend: sin red
 * (status 0), tiempo de espera (TimeoutError de rxjs) o 502-504. Cualquier
 * otra respuesta (200, 4xx, 500) prueba que hay conexión.
 */
export function esFalloDeRed(err: unknown): boolean {
  if (err instanceof HttpErrorResponse) return err.status === 0 || SIN_BACKEND.has(err.status);
  return (err as { name?: string } | null)?.name === 'TimeoutError';
}
