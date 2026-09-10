import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import { Alerta, ResumenDashboard } from '../models/alerta.model';
import { PaginaResponse } from '../models/pagina.model';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { environment } from '../../../environments/environment';

const BASE_URL = environment.apiUrl;

@Injectable({ providedIn: 'root' })
export class AlertaService {
  private readonly http = inject(HttpClient);

  private readonly _pagina = signal<PaginaResponse<Alerta> | null>(null);
  readonly pagina = this._pagina.asReadonly();

  /** La página actual, o [] mientras no haya cargado ninguna todavía. */
  readonly alertas = computed(() => this._pagina()?.contenido ?? []);

  private readonly _resumen = signal<ResumenDashboard | null>(null);
  readonly resumen = this._resumen.asReadonly();

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  /** Regla de frontend (CLAUDE.md): toda pantalla que llama a este servicio muestra este signal. */
  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  // GET /api/dashboard/resumen
  obtenerResumen(): Observable<ResumenDashboard> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http.get<ResumenDashboard>(`${BASE_URL}/dashboard/resumen`).pipe(
      tap((data) => this._resumen.set(data)),
      catchError((err) => this.manejarError(err, 'No se pudo cargar el resumen de alertas.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  // GET /api/alertas?pagina=&tamano= -- PAGINADO, sin ?orden=
  listarAlertas(pagina = 0, tamano = 20): Observable<PaginaResponse<Alerta>> {
    this._cargando.set(true);
    this._error.set(null);
    const params = new HttpParams().set('pagina', pagina).set('tamano', tamano);
    return this.http.get<PaginaResponse<Alerta>>(`${BASE_URL}/alertas`, { params }).pipe(
      tap((data) => this._pagina.set(data)),
      catchError((err) => this.manejarError(err, 'No se pudieron cargar las alertas.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  /** Molde único de manejo de error (CLAUDE.md): guarda el mensaje traducido en el signal y vuelve a lanzar. */
  private manejarError(err: HttpErrorResponse & { traducido?: ErrorTraducido }, mensajeDefecto: string): Observable<never> {
    this._error.set(err.traducido?.mensaje ?? mensajeDefecto);
    return throwError(() => err);
  }
}
