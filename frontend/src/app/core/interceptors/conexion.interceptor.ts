import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { throwError } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { ConexionService } from '../services/conexion.service';
import { esFalloDeRed } from '../utils/red.util';
import { environment } from '../../../environments/environment';

/**
 * Tarea 22 (§2): la fuente de verdad de la conexión es el resultado de las
 * peticiones a la API. Una respuesta del backend (incluido un 4xx o un 500)
 * prueba que hay conexión; status 0 o 502-504, que no. El stream SSE de
 * notificaciones no pasa por HttpClient y no cuenta.
 */
export const conexionInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(environment.apiUrl)) return next(req);
  const conexion = inject(ConexionService);
  return next(req).pipe(
    tap((evento) => {
      if (evento instanceof HttpResponse) conexion.reportarRespuesta();
    }),
    catchError((err) => {
      if (esFalloDeRed(err)) {
        conexion.reportarSinRed();
      } else {
        conexion.reportarRespuesta();
      }
      return throwError(() => err);
    }),
  );
};
