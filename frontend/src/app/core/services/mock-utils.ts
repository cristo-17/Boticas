import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';

/**
 * Envuelve un valor mock en un Observable con latencia simulada, para que
 * los servicios se consuman igual que cuando esto sea HttpClient real
 * (loading/error de verdad, no resuelto en el mismo tick). Solo para
 * mocks — al conectar el backend, este archivo deja de usarse.
 *
 * Regla de propiedad del estado (detalle en ./README.md): el servicio
 * es el único dueño de sus signals — actualiza su propio signal con
 * tap() dentro del método, antes de devolver el Observable. El
 * componente nunca guarda el resultado de un subscribe() en una
 * propiedad propia; solo lee los signals del servicio y llama a sus
 * métodos para disparar acciones.
 */
export function simulate<T>(value: T, ms = 350): Observable<T> {
  return of(value).pipe(delay(ms));
}

let nextMockId = 1000;

/** Genera ids únicos para entidades creadas en memoria por los mocks. */
export function nextId(prefix: string): string {
  nextMockId += 1;
  return `${prefix}-${nextMockId}`;
}
