import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { AlmacenOffline } from '../offline/almacen-offline';
import { EntradaCola, EnviadaConObservaciones } from '../models/offline.model';
import { NuevaVentaRequest, Venta } from '../models/venta.model';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { AuthService } from './auth.service';
import { CatalogoOfflineService } from './catalogo-offline.service';
import { ConexionService } from './conexion.service';
import { esFalloDeRed } from '../utils/red.util';
import { environment } from '../../../environments/environment';

const URL_VENTAS = `${environment.apiUrl}/ventas`;
/** §2: Render gratis tarda en despertar. Sin respuesta en este tiempo, la venta sigue en la cola. */
export const TIEMPO_ESPERA_VENTA_MS = 15_000;
/** §4: mientras haya ventas propias y conexión. */
export const INTERVALO_COLA_MS = 60_000;

/**
 * Tarea 22 (docs/DISENO-OFFLINE.md §3, §4, §9): la cola de ventas cobradas
 * sin conexión, en IndexedDB. Es la única dueña de esas entradas.
 *
 * Por qué reintentar nunca duplica: la claveIdempotencia nace UNA vez al
 * cobrar y se guarda en disco con la venta ANTES del primer envío. El
 * servidor la busca antes de validar nada o tocar el stock: si la venta ya
 * llegó (y solo se perdió la respuesta), devuelve la misma venta.
 *
 * Reglas del envío (§4, §9):
 * - una a la vez, en orden (`orden`), solo las del usuario de la sesión;
 *   las de otro usuario esperan a su dueño, nunca se mandan con otro token;
 * - sin respuesta (red, tiempo de espera, 502-504): se corta la vuelta;
 * - 401: se corta la vuelta y la cola NO se toca (el interceptor lleva al
 *   login; el mismo usuario entra y la cola sale sola);
 * - cualquier otro rechazo (400 de un bug, 403 caja ajena, 500): la entrada
 *   queda DETENIDA con el mensaje, no traba las siguientes y se reintenta
 *   en la próxima vuelta. Nunca se borra sin un 200.
 */
@Injectable({ providedIn: 'root' })
export class ColaVentasService {
  private readonly http = inject(HttpClient);
  private readonly almacen = inject(AlmacenOffline);
  private readonly auth = inject(AuthService);
  private readonly conexion = inject(ConexionService);
  private readonly catalogo = inject(CatalogoOfflineService);

  /** Todas las entradas del dispositivo, de cualquier usuario. */
  private readonly _entradas = signal<EntradaCola[]>([]);

  private readonly usuarioId = computed(() => (this.auth.usuarioActual() ? (this.auth.claims()?.usuarioId ?? null) : null));

  /** Las del usuario de la sesión, en orden de envío. */
  readonly propias = computed(() => {
    const id = this.usuarioId();
    return this._entradas()
      .filter((e) => e.usuarioId === id)
      .sort((a, b) => a.orden - b.orden);
  });
  readonly cantidadPropias = computed(() => this.propias().length);

  /** §7: "Hay 2 ventas de Rocío Paredes en este dispositivo". Solo nombre y cantidad, sin detalle. */
  readonly deOtrosUsuarios = computed(() => {
    const id = this.usuarioId();
    const porNombre = new Map<string, number>();
    for (const e of this._entradas()) {
      if (e.usuarioId !== id) porNombre.set(e.usuarioNombre, (porNombre.get(e.usuarioNombre) ?? 0) + 1);
    }
    return [...porNombre.entries()].map(([nombre, cantidad]) => ({ nombre, cantidad }));
  });

  private readonly _enviando = signal(false);
  readonly enviando = this._enviando.asReadonly();

  /** Ventas de la cola aceptadas con revisiones, en esta sesión (§7: "Enviada con observaciones"). */
  private readonly _conObservaciones = signal<EnviadaConObservaciones[]>([]);
  readonly conObservaciones = this._conObservaciones.asReadonly();

  private cargada: Promise<void> | null = null;

  constructor() {
    // Al entrar un usuario (login o app abierta con sesión): cargar la cola y vaciar la suya (§4).
    effect(() => {
      const id = this.usuarioId();
      if (id == null) return;
      untracked(() => void this.cargar().then(() => this.enviarPendientes()));
    });
    const sub = this.conexion.reconectado$.subscribe(() => void this.enviarPendientes());
    const intervalo = setInterval(() => {
      if (this.cantidadPropias() > 0 && this.conexion.enLinea()) void this.enviarPendientes();
    }, INTERVALO_COLA_MS);
    inject(DestroyRef).onDestroy(() => {
      sub.unsubscribe();
      clearInterval(intervalo);
    });
  }

  /**
   * Lee la cola de IndexedDB una vez por sesión de la app. Una entrada que quedó
   * con sinConexion = false es de un cobro en línea que nunca tuvo respuesta (la
   * app se cerró esperando): pasa a sin conexión, porque no se sabe si el cliente
   * se llevó el producto, y el servidor no la puede rechazar ni duplicar.
   */
  cargar(): Promise<void> {
    this.cargada ??= this.almacen
      .todos<EntradaCola>('cola')
      .then((todas) => this._entradas.set(todas.map((e) => (e.sinConexion ? e : { ...e, sinConexion: true }))));
    return this.cargada;
  }

  /** Escribe la entrada en disco (resuelve recién cuando IndexedDB terminó la transacción). */
  async guardar(entrada: EntradaCola): Promise<void> {
    await this.cargar();
    await this.almacen.guardar('cola', entrada.claveIdempotencia, entrada);
    this._entradas.update((todas) => [...todas.filter((e) => e.claveIdempotencia !== entrada.claveIdempotencia), entrada]);
  }

  async quitar(clave: string): Promise<void> {
    await this.almacen.borrar('cola', clave);
    this._entradas.update((todas) => todas.filter((e) => e.claveIdempotencia !== clave));
  }

  async marcarSinConexion(clave: string): Promise<void> {
    const entrada = this._entradas().find((e) => e.claveIdempotencia === clave);
    if (entrada) await this.guardar({ ...entrada, sinConexion: true });
  }

  /** El próximo número de orden: siempre mayor que cualquiera en la cola. */
  async siguienteOrden(): Promise<number> {
    await this.cargar();
    return this._entradas().reduce((max, e) => Math.max(max, e.orden), 0) + 1;
  }

  /** §8: cuántas ventas de esa caja hay en este dispositivo (cualquier estado). Solo cantidad. */
  cantidadDeCaja(cajaId: number): number {
    return this._entradas().filter((e) => e.cajaId === cajaId).length;
  }

  /** Unidades base de un producto que están en cola (el stock local las descuenta, §1). */
  unidadesEnCola(productoId: number): number {
    return this._entradas().reduce((acc, e) => acc + (e.unidadesPorProducto[productoId] ?? 0), 0);
  }

  descartarObservacion(clave: string): void {
    this._conObservaciones.update((l) => l.filter((o) => o.claveIdempotencia !== clave));
  }

  /** Una vuelta de envío (§4). Una sola a la vez: si ya hay una en curso, no hace nada. */
  async enviarPendientes(): Promise<void> {
    if (this._enviando()) return;
    // Se marca ANTES del primer await: dos disparos casi juntos (reconexión + "Enviar ahora") no arrancan dos vueltas.
    this._enviando.set(true);
    let huboExito = false;
    try {
      await this.cargar();
      if (!this.propias().some((e) => e.sinConexion)) return;
      this.conexion.marcarSincronizando(true);
      // Solo las ya cobradas sin conexión: la de un cobro en línea en curso la maneja VentaService.
      for (const entrada of this.propias().filter((e) => e.sinConexion)) {
        if (this.auth.claims()?.usuarioId !== entrada.usuarioId) break; // la sesión cambió en medio de la vuelta
        const resultado = await this.enviarUna(entrada);
        if (resultado === 'cortar') break;
        if (resultado === 'enviada') huboExito = true;
      }
    } finally {
      this._enviando.set(false);
      this.conexion.marcarSincronizando(false);
    }
    if (huboExito) this.catalogo.descargar().subscribe({ error: () => {} });
  }

  private async enviarUna(entrada: EntradaCola): Promise<'enviada' | 'detenida' | 'cortar'> {
    const cuerpo: NuevaVentaRequest = {
      ...entrada.cuerpo,
      sinConexion: { cajaId: entrada.cajaId, horaDispositivo: entrada.horaDispositivo },
    };
    try {
      const venta = await firstValueFrom(
        this.http.post<Venta>(URL_VENTAS, cuerpo).pipe(timeout(TIEMPO_ESPERA_VENTA_MS)),
      );
      await this.quitar(entrada.claveIdempotencia);
      if (venta.revisiones?.length) {
        this._conObservaciones.update((l) => [
          ...l,
          { claveIdempotencia: entrada.claveIdempotencia, ventaId: venta.id, productos: entrada.productos, revisiones: venta.revisiones! },
        ]);
      }
      return 'enviada';
    } catch (err) {
      if (esFalloDeRed(err)) {
        this.conexion.reportarSinRed(); // el tiempo de espera de rxjs no pasa por el interceptor
        return 'cortar';
      }
      if (err instanceof HttpErrorResponse && err.status === 401) return 'cortar';
      const traducido = (err as { traducido?: ErrorTraducido }).traducido;
      const mensaje =
        err instanceof HttpErrorResponse && err.status === 403
          ? 'Esta venta es de otro usuario o de otra caja.'
          : (traducido?.mensaje ?? 'El servidor no la aceptó; se reintentará.');
      await this.guardar({ ...entrada, estado: 'DETENIDA', ultimoError: mensaje });
      return 'detenida';
    }
  }
}
