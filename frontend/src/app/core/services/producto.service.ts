import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import { Producto } from '../models/producto.model';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/productos`;

@Injectable({ providedIn: 'root' })
export class ProductoService {
  private readonly http = inject(HttpClient);

  private readonly _resultados = signal<Producto[]>([]);
  readonly resultados = this._resultados.asReadonly();

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  /** Regla de frontend (CLAUDE.md): toda pantalla que llama a este servicio muestra este signal. */
  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  private readonly _masVendidos = signal<Producto[]>([]);
  readonly masVendidos = this._masVendidos.asReadonly();

  /**
   * Producto en pantalla en el modal de "qué presentación vas a vender" —
   * lo puso ahí un escaneo encontrado o una selección manual de la
   * lista de resultados. Vive aquí (no en el componente) para que la
   * regla de propiedad del estado no tenga una excepción según de
   * dónde vino el producto.
   */
  private readonly _seleccionado = signal<Producto | null>(null);
  readonly seleccionado = this._seleccionado.asReadonly();

  // GET /api/productos?buscar={query}
  buscarProductos(query: string): Observable<Producto[]> {
    this._cargando.set(true);
    this._error.set(null);
    const params = new HttpParams().set('buscar', query);
    return this.http.get<Producto[]>(BASE_URL, { params }).pipe(
      tap((data) => this._resultados.set(data)),
      catchError((err) => this.manejarError(err, 'No se pudo buscar productos.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  // GET /api/productos/codigo/{codigoBarras} — usado por el escáner. 404 PRODUCTO_NO_ENCONTRADO es un resultado esperado (código silencioso en error.interceptor.ts), no un fallo.
  buscarPorCodigoBarras(codigoBarras: string): Observable<Producto> {
    this._error.set(null);
    return this.http.get<Producto>(`${BASE_URL}/codigo/${codigoBarras}`).pipe(
      tap((data) => this._seleccionado.set(data)),
      catchError((err) => this.manejarError(err, 'No se pudo buscar el producto escaneado.')),
    );
  }

  // GET /api/productos/mas-vendidos
  obtenerMasVendidos(): Observable<Producto[]> {
    this._error.set(null);
    return this.http.get<Producto[]>(`${BASE_URL}/mas-vendidos`).pipe(
      tap((data) => this._masVendidos.set(data)),
      catchError((err) => this.manejarError(err, 'No se pudieron cargar los más vendidos.')),
    );
  }

  /**
   * Lectura síncrona sobre lo que ya está cargado en memoria (resultados
   * de la última búsqueda, más vendidos, o el seleccionado) — no hay un
   * catálogo completo en el cliente con HTTP real, a diferencia del mock
   * original. La usan VentaService y MermaScreen mientras esos módulos
   * sigan mock (Tarea 11 Bloques B/C reemplazan estos dos usos por un
   * lookup real del backend, ver docs/API-CONTRATO.md).
   */
  obtenerPorId(id: number): Producto | undefined {
    return (
      this._resultados().find((p) => p.id === id) ??
      this._masVendidos().find((p) => p.id === id) ??
      (this._seleccionado()?.id === id ? this._seleccionado()! : undefined)
    );
  }

  /** Selección manual desde la lista de resultados o los más vendidos (no hay fetch: el producto ya está cargado). */
  seleccionar(producto: Producto): void {
    this._seleccionado.set(producto);
  }

  cerrarSeleccion(): void {
    this._seleccionado.set(null);
  }

  /** Molde único de manejo de error (CLAUDE.md): guarda el mensaje traducido en el signal y vuelve a lanzar. */
  private manejarError(err: HttpErrorResponse & { traducido?: ErrorTraducido }, mensajeDefecto: string): Observable<never> {
    this._error.set(err.traducido?.mensaje ?? mensajeDefecto);
    return throwError(() => err);
  }
}
