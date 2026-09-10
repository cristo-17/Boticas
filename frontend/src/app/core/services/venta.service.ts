import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import { NuevaVentaRequest, Venta } from '../models/venta.model';
import { PaginaResponse } from '../models/pagina.model';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { AlertaService } from './alerta.service';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/ventas`;

@Injectable({ providedIn: 'root' })
export class VentaService {
  private readonly http = inject(HttpClient);
  private readonly alertaService = inject(AlertaService);

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

  // POST /api/ventas
  registrarVenta(request: Omit<NuevaVentaRequest, 'claveIdempotencia'>): Observable<Venta> {
    this._cargando.set(true);
    this._error.set(null);
    let clave = this._claveIdempotencia();
    if (!clave) {
      clave = crypto.randomUUID();
      this._claveIdempotencia.set(clave);
    }
    const cuerpo: NuevaVentaRequest = { ...request, claveIdempotencia: clave };
    return this.http.post<Venta>(BASE_URL, cuerpo).pipe(
      tap(() => {
        this._claveIdempotencia.set(null);
        this.alertaService.obtenerResumen(true).subscribe({ error: () => {} });
      }), // solo se limpia en éxito -- el reintento tras un error reusa la misma clave
      catchError((err) => this.manejarError(err, 'No se pudo registrar la venta.')),
      finalize(() => this._cargando.set(false)),
    );
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
