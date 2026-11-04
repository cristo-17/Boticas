import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import { Producto } from '../models/producto.model';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { CatalogoOfflineService } from './catalogo-offline.service';
import { ConexionService } from './conexion.service';
import { esFalloDeRed } from '../utils/red.util';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/productos`;

@Injectable({ providedIn: 'root' })
export class ProductoService {
  private readonly http = inject(HttpClient);
  private readonly conexion = inject(ConexionService);
  private readonly catalogoOffline = inject(CatalogoOfflineService);

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

  // GET /api/productos?buscar={query}&incluirInactivos= — sin incluirInactivos (Punto de Venta, alta de lote) solo
  // devuelve activos. Merma pasa true: un producto desactivado todavía tiene stock físico que retirar (Tarea 17).
  // Tarea 22: sin conexión (o si la petición se queda sin respuesta), Punto de Venta busca en la foto
  // del catálogo del dispositivo con la misma regla. Merma (incluirInactivos) no vende y no usa la foto.
  buscarProductos(query: string, incluirInactivos = false): Observable<Producto[]> {
    if (!incluirInactivos && !this.conexion.enLinea() && this.catalogoOffline.catalogo()) {
      this._error.set(null);
      const locales = this.catalogoOffline.buscar(query);
      this._resultados.set(locales);
      return of(locales);
    }
    this._cargando.set(true);
    this._error.set(null);
    let params = new HttpParams().set('buscar', query);
    if (incluirInactivos) {
      params = params.set('incluirInactivos', true);
    }
    return this.http.get<Producto[]>(BASE_URL, { params }).pipe(
      tap((data) => this._resultados.set(data)),
      catchError((err) => {
        if (!incluirInactivos && esFalloDeRed(err) && this.catalogoOffline.catalogo()) {
          const locales = this.catalogoOffline.buscar(query);
          this._resultados.set(locales);
          return of(locales);
        }
        return this.manejarError(err, 'No se pudo buscar productos.');
      }),
      finalize(() => this._cargando.set(false)),
    );
  }

  // GET /api/productos/codigo/{codigoBarras} — usado por el escáner. 404 PRODUCTO_NO_ENCONTRADO es un resultado esperado (código silencioso en error.interceptor.ts), no un fallo. 409 PRODUCTO_INACTIVO si está desactivado (Tarea 17).
  // Tarea 22: sin conexión, el escaneo de Punto de Venta busca en la foto del catálogo.
  buscarPorCodigoBarras(codigoBarras: string): Observable<Producto> {
    if (!this.conexion.enLinea() && this.catalogoOffline.catalogo()) {
      return this.porCodigoLocal(codigoBarras);
    }
    return this.consultarPorCodigoBarras(codigoBarras).pipe(
      catchError((err) =>
        esFalloDeRed(err) && this.catalogoOffline.catalogo() ? this.porCodigoLocal(codigoBarras) : throwError(() => err),
      ),
      tap((data) => this._seleccionado.set(data)),
    );
  }

  private porCodigoLocal(codigoBarras: string): Observable<Producto> {
    const producto = this.catalogoOffline.porCodigo(codigoBarras);
    if (!producto) {
      this._error.set('No se encontró un producto con ese código en el catálogo guardado.');
      return throwError(() => new Error('PRODUCTO_NO_ENCONTRADO'));
    }
    this._error.set(null);
    this._seleccionado.set(producto);
    return of(producto);
  }

  /**
   * Misma ruta, sin dejar el producto como `seleccionado` (Tarea 19: alta de
   * lote). Si lo dejara, al entrar a Punto de Venta se abriría solo el modal
   * de presentación con el producto que se escaneó en Inventario.
   */
  consultarPorCodigoBarras(codigoBarras: string): Observable<Producto> {
    this._error.set(null);
    return this.http.get<Producto>(`${BASE_URL}/codigo/${encodeURIComponent(codigoBarras)}`).pipe(
      catchError((err) => this.manejarError(err, 'No se pudo buscar el producto escaneado.')),
    );
  }

  // GET /api/productos/mas-vendidos — solo activos
  // Tarea 22: sin conexión se quedan los que ya estaban cargados (no se borran ni dan error).
  obtenerMasVendidos(): Observable<Producto[]> {
    if (!this.conexion.enLinea()) return of(this._masVendidos());
    this._error.set(null);
    return this.http.get<Producto[]>(`${BASE_URL}/mas-vendidos`).pipe(
      tap((data) => this._masVendidos.set(data)),
      catchError((err) =>
        esFalloDeRed(err) ? of(this._masVendidos()) : this.manejarError(err, 'No se pudieron cargar los más vendidos.'),
      ),
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
