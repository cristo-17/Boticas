import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import { Alerta, ResumenDashboard } from '../models/alerta.model';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/alertas`;

@Injectable({ providedIn: 'root' })
export class AlertaService {
  private readonly http = inject(HttpClient);

  private readonly _alertas = signal<Alerta[]>([]);
  readonly alertas = this._alertas.asReadonly();

  private readonly _resumen = signal<ResumenDashboard | null>(null);
  readonly resumen = this._resumen.asReadonly();

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  // GET /api/alertas/resumen
  obtenerResumen(): Observable<ResumenDashboard> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http.get<ResumenDashboard>(`${BASE_URL}/resumen`).pipe(
      tap((data) => this._resumen.set(data)),
      catchError((err) => this.manejarError(err, 'No se pudo cargar el resumen de alertas.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  // GET /api/alertas
  listarAlertas(): Observable<Alerta[]> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http.get<Alerta[]>(BASE_URL).pipe(
      tap((data) => this._alertas.set(data)),
      catchError((err) => this.manejarError(err, 'No se pudieron cargar las alertas.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  private manejarError(err: HttpErrorResponse & { traducido?: ErrorTraducido }, mensajeDefecto: string): Observable<never> {
    this._error.set(err.traducido?.mensaje ?? mensajeDefecto);
    return throwError(() => err);
  }
}
