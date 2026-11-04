import { Injectable, signal } from '@angular/core';

/**
 * Si hay una venta a medio hacer en Punto de Venta (carrito con líneas o un
 * cobro esperando respuesta). El carrito vive en un servicio de la pantalla
 * (`CarritoService`, se reinicia al salir); esto es lo único que sale de ahí,
 * para que la actualización de la app (ActualizacionAppService) nunca
 * recargue en medio de una venta. Lo escribe solo PuntoVentaScreen.
 */
@Injectable({ providedIn: 'root' })
export class VentaEnCursoService {
  private readonly _enCurso = signal(false);
  readonly enCurso = this._enCurso.asReadonly();

  marcar(enCurso: boolean): void {
    this._enCurso.set(enCurso);
  }
}
