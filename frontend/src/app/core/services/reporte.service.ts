import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { AgrupacionReporte, ReporteResultados } from '../models/reporte.model';

@Injectable({ providedIn: 'root' })
export class ReporteService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/reportes`;

  private readonly _reporte = signal<ReporteResultados | null>(null);
  private readonly _cargando = signal(false);
  private readonly _descargandoCsv = signal(false);
  private readonly _error = signal<string | null>(null);

  readonly reporte = this._reporte.asReadonly();
  readonly cargando = this._cargando.asReadonly();
  readonly descargandoCsv = this._descargandoCsv.asReadonly();
  readonly error = this._error.asReadonly();

  obtenerResultados(
    desde: string,
    hasta: string,
    agrupar: AgrupacionReporte = 'dia',
  ): Observable<ReporteResultados> {
    this._cargando.set(true);
    this._error.set(null);
    const params = new HttpParams()
      .set('desde', desde)
      .set('hasta', hasta)
      .set('agrupar', agrupar);
    return this.http.get<ReporteResultados>(`${this.apiUrl}/resultados`, { params }).pipe(
      tap((res) => this._reporte.set(res)),
      catchError((err) => this.capturarError(err)),
      finalize(() => this._cargando.set(false)),
    );
  }

  descargarCsv(
    desde: string,
    hasta: string,
    agrupar: AgrupacionReporte = 'dia',
  ): Observable<Blob> {
    this._descargandoCsv.set(true);
    this._error.set(null);
    const params = new HttpParams()
      .set('desde', desde)
      .set('hasta', hasta)
      .set('agrupar', agrupar);
    return this.http
      .get(`${this.apiUrl}/resultados/csv`, { params, responseType: 'blob' })
      .pipe(
        catchError((err) => this.capturarError(err)),
        finalize(() => this._descargandoCsv.set(false)),
      );
  }

  private capturarError(err: HttpErrorResponse & { traducido?: ErrorTraducido }): Observable<never> {
    this._error.set(err.traducido?.mensaje ?? 'No se pudo cargar el reporte de resultados.');
    return throwError(() => err);
  }
}
