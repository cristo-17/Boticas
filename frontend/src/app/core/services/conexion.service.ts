import { Injectable, signal } from '@angular/core';
import { EstadoConexion } from '../models/conexion.model';

/**
 * Estado de conectividad del dispositivo. No envuelve ningún endpoint REST
 * — en producción se alimentaría de `navigator.onLine` / eventos
 * online-offline y de una cola local de operaciones pendientes de
 * sincronizar.
 */
@Injectable({ providedIn: 'root' })
export class ConexionService {
  private readonly _estado = signal<EstadoConexion>('online');
  readonly estado = this._estado.asReadonly();

  private readonly _pendientes = signal(0);
  /** Cantidad de operaciones (ventas, mermas, aperturas…) guardadas localmente a la espera de subir. */
  readonly pendientes = this._pendientes.asReadonly();

  alternar(): void {
    this._estado.update((actual) =>
      actual === 'online' ? 'sync' : actual === 'sync' ? 'offline' : 'online',
    );
  }

  marcarPendiente(): void {
    this._pendientes.update((n) => n + 1);
  }

  marcarSincronizado(): void {
    this._pendientes.update((n) => Math.max(0, n - 1));
  }
}
