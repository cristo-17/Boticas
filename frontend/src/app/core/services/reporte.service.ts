import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { AgrupacionReporte, ReporteResultados } from '../models/reporte.model';

/**
 * Reporte de resultados — solo ADMINISTRADOR (403 SIN_PERMISO para TECNICO).
 *
 * Contrato (docs/API-CONTRATO.md, Tarea 18 Bloque C):
 * - GET /api/reportes/resultados?desde=YYYY-MM-DD&hasta=YYYY-MM-DD&agrupar=dia|semana|mes
 *   → ReporteResultados. Cada fila trae `etiqueta` y `gastos`; `totales` también trae `gastos`.
 *   400 RANGO_FECHAS_INVALIDO (desde > hasta o más de 1 año), 400 FILTRO_INVALIDO (agrupar).
 * - GET /api/reportes/resultados/xlsx?desde&hasta&agrupar
 *   → application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,
 *     archivo reporte-resultados_<desde>_<hasta>.xlsx. Reemplaza al /csv (ya no existe).
 *
 * Dos recursos con estado propio para no pisarse: `reporte` (pantalla Resultados) y
 * `tendencia` (gráfico de 7 días del Dashboard, misma ruta con agrupar=dia).
 */
@Injectable({ providedIn: 'root' })
export class ReporteService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/reportes`;

  private readonly _reporte = signal<ReporteResultados | null>(null);
  private readonly _cargando = signal(false);
  private readonly _descargandoExcel = signal(false);
  private readonly _error = signal<string | null>(null);

  private readonly _tendencia = signal<ReporteResultados | null>(null);
  private readonly _cargandoTendencia = signal(false);
  private readonly _errorTendencia = signal<string | null>(null);

  readonly reporte = this._reporte.asReadonly();
  readonly cargando = this._cargando.asReadonly();
  readonly descargandoExcel = this._descargandoExcel.asReadonly();
  readonly error = this._error.asReadonly();

  readonly tendencia = this._tendencia.asReadonly();
  readonly cargandoTendencia = this._cargandoTendencia.asReadonly();
  readonly errorTendencia = this._errorTendencia.asReadonly();

  obtenerResultados(
    desde: string,
    hasta: string,
    agrupar: AgrupacionReporte = 'dia',
  ): Observable<ReporteResultados> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http
      .get<ReporteResultados>(`${this.apiUrl}/resultados`, { params: this.params(desde, hasta, agrupar) })
      .pipe(
        tap((res) => this._reporte.set(res)),
        catchError((err) => this.capturarError(err, this._error, 'No se pudo cargar el reporte de resultados.')),
        finalize(() => this._cargando.set(false)),
      );
  }

  /** Dashboard (ADMINISTRADOR): ingresos contra gastos por día en [desde, hasta]. */
  obtenerTendencia(desde: string, hasta: string): Observable<ReporteResultados> {
    this._cargandoTendencia.set(true);
    this._errorTendencia.set(null);
    return this.http
      .get<ReporteResultados>(`${this.apiUrl}/resultados`, { params: this.params(desde, hasta, 'dia') })
      .pipe(
        tap((res) => this._tendencia.set(res)),
        catchError((err) =>
          this.capturarError(err, this._errorTendencia, 'No se pudo cargar el gráfico de los últimos 7 días.'),
        ),
        finalize(() => this._cargandoTendencia.set(false)),
      );
  }

  descargarExcel(
    desde: string,
    hasta: string,
    agrupar: AgrupacionReporte = 'dia',
  ): Observable<Blob> {
    this._descargandoExcel.set(true);
    this._error.set(null);
    return this.http
      .get(`${this.apiUrl}/resultados/xlsx`, {
        params: this.params(desde, hasta, agrupar),
        responseType: 'blob',
      })
      .pipe(
        catchError((err) => this.capturarError(err, this._error, 'No se pudo descargar el Excel.')),
        finalize(() => this._descargandoExcel.set(false)),
      );
  }

  /** Mismo nombre que manda el servidor en Content-Disposition. */
  static nombreArchivoExcel(desde: string, hasta: string): string {
    return `reporte-resultados_${desde}_${hasta}.xlsx`;
  }

  private params(desde: string, hasta: string, agrupar: AgrupacionReporte): HttpParams {
    return new HttpParams().set('desde', desde).set('hasta', hasta).set('agrupar', agrupar);
  }

  private capturarError(
    err: HttpErrorResponse & { traducido?: ErrorTraducido },
    destino: { set(v: string | null): void },
    porDefecto: string,
  ): Observable<never> {
    destino.set(err.traducido?.mensaje ?? porDefecto);
    return throwError(() => err);
  }
}
