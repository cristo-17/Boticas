import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import {
  AbrirCajaRequest,
  CajaDiaria,
  CerrarCajaRequest,
  MovimientoCaja,
  ResumenCierre,
} from '../models/caja.model';
import { PaginaResponse } from '../models/pagina.model';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { AlertaService } from './alerta.service';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/caja`;

@Injectable({ providedIn: 'root' })
export class CajaService {
  private readonly http = inject(HttpClient);
  private readonly alertaService = inject(AlertaService);

  private readonly _cajaActual = signal<CajaDiaria | null>(null);
  readonly cajaActual = this._cajaActual.asReadonly();

  /** Se llena ANTES de contar (GET /resumen-cierre) — deliberadamente sin montoEsperado (conteo ciego). */
  private readonly _resumenCierre = signal<ResumenCierre | null>(null);
  readonly resumenCierre = this._resumenCierre.asReadonly();

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
      tap((caja) => this._cajaActual.set(caja)),
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

  // POST /api/caja/cerrar — el servidor calcula montoEsperado/diferencia/semaforoDescuadre; recién acá se conocen.
  cerrarCaja(request: CerrarCajaRequest): Observable<CajaDiaria> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http.post<CajaDiaria>(`${BASE_URL}/cerrar`, request).pipe(
      tap((caja) => {
        this._cajaActual.set(caja);
        this.alertaService.obtenerResumen(true).subscribe({ error: () => {} });
      }),
      catchError((err) => this.manejarError(err, 'No se pudo cerrar la caja.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  /** Molde único de manejo de error (CLAUDE.md): guarda el mensaje traducido en el signal y vuelve a lanzar. */
  private manejarError(err: HttpErrorResponse & { traducido?: ErrorTraducido }, mensajeDefecto: string): Observable<never> {
    this._error.set(err.traducido?.mensaje ?? mensajeDefecto);
    return throwError(() => err);
  }
}
