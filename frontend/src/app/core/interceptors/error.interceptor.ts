import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

/** Forma exacta del error del backend (Regla 7, docs/API-CONTRATO.md). */
export interface ErrorBackend {
  timestamp: string;
  status: number;
  error: string;
  mensaje: string;
  path: string;
}

/** Metadata que este interceptor agrega al HttpErrorResponse original, sin reemplazarlo. */
export interface ErrorTraducido {
  codigo: string | null;
  mensaje: string;
  /** true = resultado esperado del flujo, no una falla — la pantalla no debe mostrar toast. */
  silencioso: boolean;
}

/**
 * Códigos que no muestran toast: son resultados esperados del flujo, no
 * fallas. Empieza con PRODUCTO_NO_ENCONTRADO (flujo de escaneo en Punto
 * de Venta) aunque ese módulo recién se conecta en la Tarea 11 — la lista
 * queda preparada desde ahora.
 */
const CODIGOS_SILENCIOSOS = new Set<string>(['PRODUCTO_NO_ENCONTRADO']);

/** Traduce el código "error" del backend al mensaje que cada pantalla muestra. Un solo lugar, no ifs desperdigados. */
const MENSAJES_POR_CODIGO: Record<string, string> = {
  CAJA_YA_ABIERTA: 'Ya existe una caja abierta hoy.',
  CAJA_NO_ABIERTA: 'No hay una caja abierta para esta operación.',
  CAJA_NO_ENCONTRADA: 'La caja indicada no existe.',
  MONTO_INVALIDO: 'El monto no puede ser negativo.',
  ORDEN_INVALIDO: 'No se puede ordenar por ese campo.',
  FORMATO_INVALIDO: 'Revisa los datos ingresados.',
  PRODUCTO_NO_ENCONTRADO: 'No se encontró un producto con ese código.',
  SIN_CAJA_ABIERTA: 'No hay una caja abierta para registrar la venta.',
  STOCK_INSUFICIENTE: 'No hay stock suficiente para completar la venta.',
  PRESENTACION_INVALIDA: 'La presentación seleccionada ya no es válida para ese producto.',
  ERROR_INTERNO: 'Ocurrió un error inesperado. Intenta de nuevo.',
};

function traducir(respuesta: HttpErrorResponse): ErrorTraducido {
  const cuerpo = respuesta.error as Partial<ErrorBackend> | null;
  const codigo = cuerpo?.error ?? null;
  if (!codigo) {
    // Sin cuerpo de error del backend: red caída, timeout, backend apagado.
    return { codigo: null, mensaje: 'No se pudo conectar con el servidor. Verifica tu conexión.', silencioso: false };
  }
  return {
    codigo,
    mensaje: MENSAJES_POR_CODIGO[codigo] ?? cuerpo?.mensaje ?? 'Ocurrió un error inesperado.',
    silencioso: CODIGOS_SILENCIOSOS.has(codigo),
  };
}

/**
 * Adjunta la traducción al mismo HttpErrorResponse y lo vuelve a lanzar
 * (no cambia su forma): cada pantalla sigue manejando su propio
 * `error: (err: HttpErrorResponse) => ...`, pero ahora puede leer
 * `err.traducido.mensaje` / `err.traducido.silencioso` en vez de tener
 * su propio texto hardcodeado.
 */
/**
 * Un 401 cierra sesión y redirige a login (Tarea 12) -- salvo en el
 * propio POST /auth/login, donde un 401 es "contraseña incorrecta",
 * no "tu sesión murió" (si no se excluye, la pantalla de login se
 * redirigiría a sí misma en cada intento fallido).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return next(req).pipe(
    catchError((respuesta: HttpErrorResponse) => {
      (respuesta as HttpErrorResponse & { traducido: ErrorTraducido }).traducido = traducir(respuesta);
      if (respuesta.status === 401 && !req.url.endsWith('/auth/login')) {
        auth.limpiarSesionLocal();
        router.navigateByUrl('/login');
      }
      return throwError(() => respuesta);
    }),
  );
};
