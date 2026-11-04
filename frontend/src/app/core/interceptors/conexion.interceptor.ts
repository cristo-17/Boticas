import { HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { TimeoutError, throwError } from 'rxjs';
import { catchError, tap, timeout } from 'rxjs/operators';
import { ConexionService } from '../services/conexion.service';
import { esFalloDeRed } from '../utils/red.util';
import { errorSinConexion } from './error.interceptor';
import { environment } from '../../../environments/environment';

/**
 * Tiempo máximo de una petición a la API que no es la venta. Alto a propósito: Render
 * gratis tarda hasta ~50 s en despertar, y eso no es "sin conexión". La venta tiene el
 * suyo (15 s, TIEMPO_ESPERA_VENTA_MS) y su cola.
 */
export const TIEMPO_ESPERA_API_MS = 60_000;

/** POST /api/ventas: la decide VentaService (cola, idempotencia, su propio tiempo de espera). */
function esLaVenta(req: HttpRequest<unknown>): boolean {
  return req.method === 'POST' && req.url === `${environment.apiUrl}/ventas`;
}

/**
 * Tarea 22 (§2): la fuente de verdad de la conexión es el resultado de las
 * peticiones a la API. Una respuesta del backend (incluido un 4xx o un 500)
 * prueba que hay conexión; status 0 o 502-504, que no. El stream SSE de
 * notificaciones no pasa por HttpClient y no cuenta.
 *
 * offline-fix (punto 3): sin conexión, toda petición que no sea la venta falla
 * AL INSTANTE con "Esta acción necesita conexión" en vez de quedar colgada (en
 * el celular, "Abrir caja" se quedaba cargando sin fin). Con conexión, si el
 * servidor no contesta en TIEMPO_ESPERA_API_MS, el mismo error. La comprobación
 * de ConexionService no pasa por aquí (usa HttpBackend).
 */
export const conexionInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(environment.apiUrl)) return next(req);
  const conexion = inject(ConexionService);
  const venta = esLaVenta(req);
  if (!venta && !conexion.enLinea()) {
    void conexion.comprobar(); // si la señal ya volvió, la próxima acción sale
    return throwError(() => errorSinConexion(req.url));
  }
  const envio$ = venta ? next(req) : next(req).pipe(timeout(TIEMPO_ESPERA_API_MS));
  return envio$.pipe(
    tap((evento) => {
      if (evento instanceof HttpResponse) conexion.reportarRespuesta();
    }),
    catchError((err) => {
      if (esFalloDeRed(err)) {
        conexion.reportarSinRed();
      } else {
        conexion.reportarRespuesta();
      }
      return throwError(() => (err instanceof TimeoutError ? errorSinConexion(req.url) : err));
    }),
  );
};
