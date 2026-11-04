import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import {
  AbrirCajaRequest,
  CajaDiaria,
  CerrarCajaRequest,
  MovimientoCaja,
  RegistrarEgresoRequest,
  ResumenCierre,
} from '../models/caja.model';
import { PaginaResponse } from '../models/pagina.model';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { AlertaService } from './alerta.service';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/caja`;
/** Tarea 22: la última caja abierta conocida, por usuario, para vender sin conexión (la venta lleva su cajaId). */
const PREFIJO_CAJA_CONOCIDA = 'boticasys_caja_';

@Injectable({ providedIn: 'root' })
export class CajaService {
  private readonly http = inject(HttpClient);
  private readonly alertaService = inject(AlertaService);
  private readonly auth = inject(AuthService);

  private readonly _cajaActual = signal<CajaDiaria | null>(null);
  readonly cajaActual = this._cajaActual.asReadonly();

  /** Se llena ANTES de contar (GET /resumen-cierre) — deliberadamente sin montoEsperado (conteo ciego). */
  private readonly _resumenCierre = signal<ResumenCierre | null>(null);
  readonly resumenCierre = this._resumenCierre.asReadonly();

  /** D-E2-1: solo ADMINISTRADOR. Sin montos que revelen el esperado (CajaResponse de una caja abierta no los trae). */
  private readonly _abiertas = signal<PaginaResponse<CajaDiaria> | null>(null);
  readonly abiertas = this._abiertas.asReadonly();

  /** La última caja cerrada desde esta sesión -- la del ADMINISTRADOR puede no ser cajaActual. */
  private readonly _ultimoCierre = signal<CajaDiaria | null>(null);
  readonly ultimoCierre = this._ultimoCierre.asReadonly();

  private readonly _movimientos = signal<PaginaResponse<MovimientoCaja> | null>(null);
  readonly movimientos = this._movimientos.asReadonly();

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  /** Regla de frontend (CLAUDE.md): toda pantalla que llama a este servicio muestra este signal. */
  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  // GET /api/caja/hoy
  obtenerCajaDeHoy(): Observable<CajaDiaria | null> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http.get<CajaDiaria | null>(`${BASE_URL}/hoy`).pipe(
      tap((caja) => {
        this._cajaActual.set(caja);
        this.recordarCaja(caja);
      }),
      catchError((err) => this.manejarError(err, 'No se pudo cargar la caja de hoy.')),
      // finalize corre en éxito Y en error -- tap() sola habría dejado
      // "cargando" en true para siempre ante un fallo (409, red caída, etc.).
      finalize(() => this._cargando.set(false)),
    );
  }

  // POST /api/caja/abrir
  abrirCaja(request: AbrirCajaRequest): Observable<CajaDiaria> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http.post<CajaDiaria>(`${BASE_URL}/abrir`, request).pipe(
      tap((caja) => {
        this._cajaActual.set(caja);
        this.recordarCaja(caja);
        this._resumenCierre.set(null);
        this._movimientos.set(null);
        this.alertaService.obtenerResumen(true).subscribe({ error: () => {} });
      }),
      catchError((err) => this.manejarError(err, 'No se pudo abrir la caja.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  // GET /api/caja/{id}/resumen-cierre — llamar ANTES de que el cajero cuente el efectivo.
  obtenerResumenCierre(): Observable<ResumenCierre> {
    const id = this._cajaActual()?.id;
    if (id === undefined) {
      throw new Error('No hay una caja abierta para pedir el resumen de cierre.');
    }
    this._error.set(null);
    return this.http.get<ResumenCierre>(`${BASE_URL}/${id}/resumen-cierre`).pipe(
      tap((resumen) => this._resumenCierre.set(resumen)),
      catchError((err) => this.manejarError(err, 'No se pudo cargar el resumen de cierre.')),
    );
  }

  // GET /api/caja/{id}/movimientos?pagina=&tamano=&orden=
  listarMovimientos(pagina = 0, tamano = 20, orden?: string): Observable<PaginaResponse<MovimientoCaja>> {
    const id = this._cajaActual()?.id;
    if (id === undefined) {
      throw new Error('No hay una caja abierta para listar movimientos.');
    }
    let params = new HttpParams().set('pagina', pagina).set('tamano', tamano);
    if (orden) {
      params = params.set('orden', orden);
    }
    this._error.set(null);
    return this.http.get<PaginaResponse<MovimientoCaja>>(`${BASE_URL}/${id}/movimientos`, { params }).pipe(
      tap((data) => this._movimientos.set(data)),
      catchError((err) => this.manejarError(err, 'No se pudieron cargar los movimientos.')),
    );
  }

  // GET /api/caja/abiertas?pagina=&tamano= (solo ADMINISTRADOR, D-E2-1)
  listarAbiertas(pagina = 0, tamano = 20): Observable<PaginaResponse<CajaDiaria>> {
    this._cargando.set(true);
    this._error.set(null);
    const params = new HttpParams().set('pagina', pagina).set('tamano', tamano);
    return this.http.get<PaginaResponse<CajaDiaria>>(`${BASE_URL}/abiertas`, { params }).pipe(
      tap((data) => this._abiertas.set(data)),
      catchError((err) => this.manejarError(err, 'No se pudieron cargar las cajas abiertas.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  // POST /api/caja/egresos (Tarea 18, Bloque A)
  registrarEgreso(request: RegistrarEgresoRequest): Observable<MovimientoCaja> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http.post<MovimientoCaja>(`${BASE_URL}/egresos`, request).pipe(
      catchError((err) => this.manejarError(err, 'No se pudo registrar el gasto de caja.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  // POST /api/caja/cerrar — el servidor calcula montoEsperado/diferencia/semaforoDescuadre; recién acá se conocen.
  cerrarCaja(request: CerrarCajaRequest): Observable<CajaDiaria> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http.post<CajaDiaria>(`${BASE_URL}/cerrar`, request).pipe(
      tap((caja) => {
        this._ultimoCierre.set(caja);
        // Sin cajaId es la propia; con cajaId puede ser la de otro usuario y no tocar cajaActual.
        if (request.cajaId === undefined || this._cajaActual()?.id === caja.id) {
          this._cajaActual.set(caja);
          this.recordarCaja(caja);
        }
        this._abiertas.update((p) =>
          p ? { ...p, contenido: p.contenido.filter((c) => c.id !== caja.id), totalElementos: p.totalElementos - 1 } : p,
        );
        this.alertaService.obtenerResumen(true).subscribe({ error: () => {} });
      }),
      catchError((err) => this.manejarError(err, 'No se pudo cerrar la caja.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  /**
   * Tarea 22 (§3, §9): la caja abierta del usuario de la sesión, para una venta
   * sin conexión: la de memoria si está abierta, si no la última que el servidor
   * dijo que estaba abierta (guardada al abrirla o al consultar /hoy). null si no
   * hay ninguna conocida: sin caja abierta no se vende, ni con conexión ni sin ella.
   */
  cajaAbiertaConocida(): CajaDiaria | null {
    const actual = this._cajaActual();
    if (actual?.abierta) return actual;
    const usuarioId = this.auth.claims()?.usuarioId;
    if (usuarioId == null || typeof localStorage === 'undefined') return null;
    try {
      const texto = localStorage.getItem(PREFIJO_CAJA_CONOCIDA + usuarioId);
      const caja = texto ? (JSON.parse(texto) as CajaDiaria) : null;
      return caja?.abierta ? caja : null;
    } catch {
      return null;
    }
  }

  private recordarCaja(caja: CajaDiaria | null): void {
    const usuarioId = this.auth.claims()?.usuarioId;
    if (usuarioId == null || typeof localStorage === 'undefined') return;
    if (caja?.abierta) {
      localStorage.setItem(PREFIJO_CAJA_CONOCIDA + usuarioId, JSON.stringify(caja));
    } else {
      localStorage.removeItem(PREFIJO_CAJA_CONOCIDA + usuarioId);
    }
  }

  /** Molde único de manejo de error (CLAUDE.md): guarda el mensaje traducido en el signal y vuelve a lanzar. */
  private manejarError(err: HttpErrorResponse & { traducido?: ErrorTraducido }, mensajeDefecto: string): Observable<never> {
    this._error.set(err.traducido?.mensaje ?? mensajeDefecto);
    return throwError(() => err);
  }
}
