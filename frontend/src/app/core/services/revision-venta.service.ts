import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { PaginaResponse } from '../models/pagina.model';
import { ResolucionRevision, RevisionVenta } from '../models/venta.model';
import { AlertaService } from './alerta.service';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/ventas/revisiones`;

/**
 * Tarea 22 (D-E2-7): revisiones de las ventas sin conexión — solo ADMINISTRADOR
 * (403 SIN_PERMISO para TECNICO).
 *
 * Contrato (docs/API-CONTRATO.md, Tarea 22):
 * - GET /api/ventas/revisiones?estado=PENDIENTE|RESUELTA&pagina=&tamano= — PAGINADO, más
 *   recientes primero. Sin estado = todas. Otro estado → 400 FILTRO_INVALIDO.
 *   Cada RevisionVenta trae ventaFecha, ventaTotal y vendedorNombre.
 * - POST /api/ventas/revisiones/{id}/resolver { accion: 'VISTA' | 'FALTANTE', nota } → RevisionVenta.
 *   FALTANTE solo para STOCK_INSUFICIENTE y con nota (400 RESOLUCION_INVALIDA).
 *   404 REVISION_NO_ENCONTRADA, 409 REVISION_YA_RESUELTA (no se reabre).
 */
@Injectable({ providedIn: 'root' })
export class RevisionVentaService {
  private readonly http = inject(HttpClient);
  private readonly alertaService = inject(AlertaService);

  private readonly _pagina = signal<PaginaResponse<RevisionVenta> | null>(null);
  readonly pagina = this._pagina.asReadonly();

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  /** Regla de frontend (CLAUDE.md): toda pantalla que llama a este servicio muestra este signal. */
  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  listar(estado: 'PENDIENTE' | 'RESUELTA' | null, pagina = 0, tamano = 20): Observable<PaginaResponse<RevisionVenta>> {
    this._cargando.set(true);
    this._error.set(null);
    let params = new HttpParams().set('pagina', pagina).set('tamano', tamano);
    if (estado) params = params.set('estado', estado);
    return this.http.get<PaginaResponse<RevisionVenta>>(BASE_URL, { params }).pipe(
      tap((p) => this._pagina.set(p)),
      catchError((err) => this.manejarError(err, 'No se pudieron cargar las ventas por revisar.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  resolver(id: number, accion: ResolucionRevision, nota: string | null): Observable<RevisionVenta> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http.post<RevisionVenta>(`${BASE_URL}/${id}/resolver`, { accion, nota }).pipe(
      tap((resuelta) => {
        this._pagina.update((p) =>
          p ? { ...p, contenido: p.contenido.map((r) => (r.id === resuelta.id ? resuelta : r)) } : p,
        );
        this.alertaService.obtenerResumen(true).subscribe({ error: () => {} });
      }),
      catchError((err) => this.manejarError(err, 'No se pudo resolver la revisión.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  /** Molde único de manejo de error (CLAUDE.md): guarda el mensaje traducido en el signal y vuelve a lanzar. */
  private manejarError(err: HttpErrorResponse & { traducido?: ErrorTraducido }, mensajeDefecto: string): Observable<never> {
    this._error.set(err.traducido?.mensaje ?? mensajeDefecto);
    return throwError(() => err);
  }
}
