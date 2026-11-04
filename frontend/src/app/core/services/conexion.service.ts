import { HttpBackend, HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { Subject, firstValueFrom } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { EstadoConexion } from '../models/conexion.model';
import { environment } from '../../../environments/environment';

/** Comprobación mientras se está sin conexión: cada 30 s, espaciándose hasta 2 min (§2). */
export const ESPERA_INICIAL_MS = 30_000;
export const ESPERA_MAXIMA_MS = 120_000;
const TIEMPO_COMPROBACION_MS = 10_000;

/**
 * Tarea 22 (docs/DISENO-OFFLINE.md §2): estado real de la conexión. No
 * envuelve ningún recurso REST: lo alimenta el resultado de las peticiones
 * a la API (conexionInterceptor) y, mientras se está sin conexión, una
 * comprobación a GET /api/config.
 * - navigator.onLine solo se cree cuando dice "no" (evento offline). El
 *   evento online solo dispara una comprobación: con wifi sin internet dice
 *   "en línea".
 * - La comprobación cuenta solo si responde JSON con `version`: un portal
 *   cautivo responde 200 con HTML, y eso no es conexión.
 * - `sync` = hay red y la cola de ventas se está enviando (ColaVentasService).
 */
@Injectable({ providedIn: 'root' })
export class ConexionService {
  /** Sin interceptores: la comprobación no debe pasar por conexionInterceptor ni por el 401 del errorInterceptor. */
  private readonly httpCrudo = new HttpClient(inject(HttpBackend));

  private readonly _hayRed = signal(true);
  private readonly _sincronizando = signal(false);
  readonly estado = computed<EstadoConexion>(() =>
    !this._hayRed() ? 'offline' : this._sincronizando() ? 'sync' : 'online',
  );
  readonly enLinea = computed(() => this._hayRed());

  /** Emite cada vez que se pasa de sin conexión a con conexión: la cola se vacía ahí (§4). */
  private readonly _reconectado = new Subject<void>();
  readonly reconectado$ = this._reconectado.asObservable();

  private temporizador: ReturnType<typeof setTimeout> | null = null;
  private espera = ESPERA_INICIAL_MS;
  private comprobando = false;

  constructor() {
    if (typeof window !== 'undefined') {
      const alPerder = () => this.reportarSinRed();
      const alVolver = () => void this.comprobar();
      window.addEventListener('offline', alPerder);
      window.addEventListener('online', alVolver);
      inject(DestroyRef).onDestroy(() => {
        window.removeEventListener('offline', alPerder);
        window.removeEventListener('online', alVolver);
        this.detenerTemporizador();
      });
      if (typeof navigator !== 'undefined' && navigator.onLine === false) this.reportarSinRed();
    }
  }

  /** Una petición a la API tuvo respuesta del backend (cualquier código salvo 502-504). */
  reportarRespuesta(): void {
    if (this._hayRed()) return;
    this._hayRed.set(true);
    this.detenerTemporizador();
    this.espera = ESPERA_INICIAL_MS;
    this._reconectado.next();
  }

  /** Una petición a la API se quedó sin respuesta (status 0, tiempo de espera o 502-504). */
  reportarSinRed(): void {
    if (this._hayRed()) {
      this._hayRed.set(false);
      this.espera = ESPERA_INICIAL_MS;
    }
    this.programarComprobacion();
  }

  marcarSincronizando(valor: boolean): void {
    this._sincronizando.set(valor);
  }

  /** GET /api/config sin interceptores. true solo con JSON que trae `version`. */
  async comprobar(): Promise<boolean> {
    if (this.comprobando) return this._hayRed();
    this.comprobando = true;
    try {
      const cuerpo = await firstValueFrom(
        this.httpCrudo
          .get<unknown>(`${environment.apiUrl}/config`, { headers: { 'Cache-Control': 'no-cache' } })
          .pipe(timeout(TIEMPO_COMPROBACION_MS)),
      );
      const valido = typeof cuerpo === 'object' && cuerpo !== null && 'version' in cuerpo;
      if (valido) {
        this.reportarRespuesta();
      } else {
        this.reportarSinRed();
      }
      return valido;
    } catch {
      this.reportarSinRed();
      return false;
    } finally {
      this.comprobando = false;
    }
  }

  private programarComprobacion(): void {
    if (this.temporizador) return;
    this.temporizador = setTimeout(() => {
      this.temporizador = null;
      this.espera = Math.min(this.espera * 2, ESPERA_MAXIMA_MS);
      void this.comprobar();
    }, this.espera);
  }

  private detenerTemporizador(): void {
    if (this.temporizador) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
  }
}
