import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, defer, from, throwError } from 'rxjs';
import { catchError, finalize, map, switchMap, tap, timeout } from 'rxjs/operators';
import { NuevaVentaRequest, Venta } from '../models/venta.model';
import { EntradaCola } from '../models/offline.model';
import { AuthService } from './auth.service';
import { CajaService } from './caja.service';
import { ColaVentasService, TIEMPO_ESPERA_VENTA_MS } from './cola-ventas.service';
import { ConexionService } from './conexion.service';
import { esFalloDeRed } from '../utils/red.util';
import { PaginaResponse } from '../models/pagina.model';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { AlertaService } from './alerta.service';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/ventas`;
/** offline-fix (punto 2): sin caja abierta conocida en este equipo no hay venta sin conexión. */
export const MENSAJE_SIN_CAJA_SIN_CONEXION = 'Abre la caja con conexión antes de vender sin conexión.';
export const MENSAJE_SESION_SIN_CONEXION = 'Tu sesión venció: vuelve a entrar con conexión para vender sin conexión.';

/**
 * Tarea 22: resultado de cobrar. `registrada` = el servidor respondió 200;
 * `pendiente` = no hubo respuesta: la venta quedó en la cola del dispositivo
 * (ya ocurrió en el mostrador) y se enviará sola.
 */
export type ResultadoCobro = { tipo: 'registrada'; venta: Venta } | { tipo: 'pendiente'; claveIdempotencia: string };

/** Tarea 22: lo que la cola necesita además del cuerpo (§3, §7): sin montos. */
export interface DetalleParaCola {
  productos: string[];
  unidadesPorProducto: Record<number, number>;
}

/** Error propio cuando ya se sabe que no hay conexión: ni se intenta el POST. */
class SinConexionConocida extends Error {
  override readonly name = 'TimeoutError';
}

@Injectable({ providedIn: 'root' })
export class VentaService {
  private readonly http = inject(HttpClient);
  private readonly alertaService = inject(AlertaService);
  private readonly auth = inject(AuthService);
  private readonly cajaService = inject(CajaService);
  private readonly cola = inject(ColaVentasService);
  private readonly conexion = inject(ConexionService);

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  /** Regla de frontend (CLAUDE.md): toda pantalla que llama a este servicio muestra este signal. */
  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  private readonly _pagina = signal<PaginaResponse<Venta> | null>(null);
  readonly pagina = this._pagina.asReadonly();

  /**
   * Se genera al confirmar el carrito (la primera vez que se llama a
   * registrarVenta para este intento de cobro), NO dentro de cada
   * intento HTTP -- si se generara ahí, cada reintento de red mandaría
   * un UUID distinto y la idempotencia no serviría de nada. Se reusa
   * en cada reintento del mismo POST (nunca se limpia en catchError) y
   * solo se limpia cuando el backend responde con éxito.
   */
  private readonly _claveIdempotencia = signal<string | null>(null);

  // POST /api/ventas — NuevaVentaRequest. Tarea 20: con alguna línea de un producto con
  // requiereReceta, la receta es obligatoria → 400 RECETA_REQUERIDA (el mensaje del servidor
  // nombra los productos). 400 RECETA_INVALIDA (fecha de emisión futura), 400 FORMATO_INVALIDO
  // (sin número o CMP que no sean de 1 a 6 dígitos). La respuesta trae `receta` (o null).
  // Tarea 22 (§3): primero se escribe la venta en la cola (IndexedDB) y después se envía. Con
  // respuesta del servidor, la entrada se borra (200 o error de negocio: la venta no ocurrió).
  // Sin respuesta (red, 15 s, 502-504) o ya sin conexión, queda en la cola como venta sin
  // conexión y el resultado es `pendiente`. items[].precioCobrado viaja siempre: en línea el
  // servidor lo ignora. Sin una caja abierta conocida no hay cola: es el flujo de antes.
  registrarVenta(
    request: Omit<NuevaVentaRequest, 'claveIdempotencia' | 'sinConexion'>,
    detalle: DetalleParaCola = { productos: [], unidadesPorProducto: {} },
  ): Observable<ResultadoCobro> {
    this._cargando.set(true);
    this._error.set(null);
    let clave = this._claveIdempotencia();
    if (!clave) {
      clave = crypto.randomUUID();
      this._claveIdempotencia.set(clave);
    }
    const claveFija = clave;
    const cuerpo: Omit<NuevaVentaRequest, 'sinConexion'> = { ...request, claveIdempotencia: claveFija };

    return defer(() => from(this.prepararEntrada(cuerpo, detalle))).pipe(
      switchMap((entrada) => {
        // offline-fix (punto 2): sin cola posible (sin caja abierta conocida o sin sesión vigente) y
        // sin conexión, la venta no sale: se dice qué hacer y el carrito queda como estaba.
        if (!entrada && !this.conexion.enLinea()) return this.sinColaPosible();
        const envio$ =
          entrada && !this.conexion.enLinea()
            ? throwError(() => new SinConexionConocida())
            : this.http.post<Venta>(BASE_URL, cuerpo).pipe(timeout(TIEMPO_ESPERA_VENTA_MS));
        return envio$.pipe(
          switchMap((venta) =>
            from(entrada ? this.cola.quitar(claveFija) : Promise.resolve()).pipe(
              map((): ResultadoCobro => ({ tipo: 'registrada', venta })),
            ),
          ),
          catchError((err) => {
            if (!entrada && esFalloDeRed(err)) return this.sinColaPosible();
            if (entrada && esFalloDeRed(err)) {
              this.conexion.reportarSinRed(); // el tiempo de espera de rxjs no pasa por el interceptor
              return from(this.cola.marcarSinConexion(claveFija)).pipe(
                map((): ResultadoCobro => ({ tipo: 'pendiente', claveIdempotencia: claveFija })),
              );
            }
            // Con respuesta del servidor la venta no ocurrió: fuera de la cola, error en pantalla (como antes).
            return from(entrada ? this.cola.quitar(claveFija) : Promise.resolve()).pipe(
              switchMap(() => this.manejarError(err, 'No se pudo registrar la venta.')),
            );
          }),
        );
      }),
      tap((resultado) => {
        // registrada o en la cola: en los dos casos este cobro terminó y el próximo lleva clave nueva.
        this._claveIdempotencia.set(null);
        if (resultado.tipo === 'registrada') {
          this.alertaService.obtenerResumen(true).subscribe({ error: () => {} });
        }
      }), // tras un error de negocio la clave se conserva: el reintento reusa la misma
      finalize(() => this._cargando.set(false)),
    );
  }

  /** El cobro no puede quedar en la cola: error en pantalla con lo que hay que hacer; el carrito no se toca. */
  private sinColaPosible(): Observable<never> {
    const sinCaja = !this.cajaService.cajaAbiertaConocida();
    this._error.set(sinCaja ? MENSAJE_SIN_CAJA_SIN_CONEXION : MENSAJE_SESION_SIN_CONEXION);
    return throwError(() => new Error(sinCaja ? 'SIN_CAJA_SIN_CONEXION' : 'SESION_SIN_CONEXION'));
  }

  /**
   * La entrada de la cola, ya escrita en disco. null si no se puede vender sin
   * conexión (sin caja abierta conocida o sin sesión vigente, §9): entonces la
   * venta va solo en línea, como antes de la Tarea 22.
   */
  private async prepararEntrada(cuerpo: Omit<NuevaVentaRequest, 'sinConexion'>, detalle: DetalleParaCola): Promise<EntradaCola | null> {
    const caja = this.cajaService.cajaAbiertaConocida();
    const claims = this.auth.claims();
    const usuario = this.auth.usuarioActual();
    if (!caja || !claims || !usuario || !this.auth.sesionVigente()) return null;
    const entrada: EntradaCola = {
      claveIdempotencia: cuerpo.claveIdempotencia,
      orden: await this.cola.siguienteOrden(),
      usuarioId: claims.usuarioId,
      usuarioNombre: usuario.nombre,
      boticaId: claims.boticaId,
      cajaId: caja.id,
      horaDispositivo: horaLocalIso(new Date()),
      sinConexion: false,
      estado: 'PENDIENTE',
      ultimoError: null,
      cuerpo,
      productos: detalle.productos,
      unidadesPorProducto: detalle.unidadesPorProducto,
    };
    await this.cola.guardar(entrada);
    return entrada;
  }

  // GET /api/ventas
  listarVentas(pagina = 0, tamano = 20, orden = 'fecha,desc'): Observable<PaginaResponse<Venta>> {
    this._cargando.set(true);
    this._error.set(null);
    const params = new HttpParams()
      .set('pagina', pagina.toString())
      .set('tamano', tamano.toString())
      .set('orden', orden);

    return this.http.get<PaginaResponse<Venta>>(BASE_URL, { params }).pipe(
      tap((resp) => this._pagina.set(resp)),
      catchError((err) => this.manejarError(err, 'No se pudieron cargar las ventas.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  // GET /api/ventas/:id
  obtenerVenta(id: number): Observable<Venta> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http.get<Venta>(`${BASE_URL}/${id}`).pipe(
      catchError((err) => this.manejarError(err, 'No se pudo obtener la venta.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  /** Molde único de manejo de error (CLAUDE.md): guarda el mensaje traducido en el signal y vuelve a lanzar. */
  private manejarError(err: HttpErrorResponse & { traducido?: ErrorTraducido }, mensajeDefecto: string): Observable<never> {
    this._error.set(err.traducido?.mensaje ?? mensajeDefecto);
    return throwError(() => err);
  }
}

/** ISO con el desfase del reloj del dispositivo (p. ej. 2026-11-03T14:05:12.345-05:00): lo que dijo el aparato, tal cual. */
function horaLocalIso(d: Date): string {
  const off = -d.getTimezoneOffset();
  const signo = off >= 0 ? '+' : '-';
  const dos = (n: number) => String(Math.floor(Math.abs(n))).padStart(2, '0');
  const local = new Date(d.getTime() + off * 60_000).toISOString().slice(0, 23);
  return `${local}${signo}${dos(off / 60)}:${dos(off % 60)}`;
}
