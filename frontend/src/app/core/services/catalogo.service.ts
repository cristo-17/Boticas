import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import { ProductoCatalogo, ProductoRequest } from '../models/catalogo.model';
import { PaginaResponse } from '../models/pagina.model';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/productos`;

/**
 * Catálogo de productos, solo ADMINISTRADOR (Tarea 17, Bloque A; R17).
 * Dos pares de estado separados: el del listado (cargando/error) y el del
 * formulario (guardando/errorGuardado), para que un error al guardar se
 * muestre en el formulario y no tape la lista, ni al revés.
 */
@Injectable({ providedIn: 'root' })
export class CatalogoService {
  private readonly http = inject(HttpClient);

  private readonly _pagina = signal<PaginaResponse<ProductoCatalogo> | null>(null);
  readonly pagina = this._pagina.asReadonly();
  readonly productos = computed(() => this._pagina()?.contenido ?? []);

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  /** Regla de frontend (CLAUDE.md): toda pantalla que llama a este servicio muestra este signal. */
  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  private readonly _guardando = signal(false);
  readonly guardando = this._guardando.asReadonly();

  private readonly _errorGuardado = signal<string | null>(null);
  readonly errorGuardado = this._errorGuardado.asReadonly();

  // GET /api/productos/catalogo?buscar=&pagina=&tamano=&orden= — PAGINADO, solo ADMINISTRADOR.
  // Activos e inactivos, con todas sus presentaciones. orden: nombre|categoria|laboratorio (,asc|,desc);
  // otro campo → 400 ORDEN_INVALIDO. TECNICO → 403 SIN_PERMISO.
  listar(buscar = '', pagina = 0, tamano = 20, orden?: string): Observable<PaginaResponse<ProductoCatalogo>> {
    this._cargando.set(true);
    this._error.set(null);
    let params = new HttpParams().set('buscar', buscar).set('pagina', pagina).set('tamano', tamano);
    if (orden) {
      params = params.set('orden', orden);
    }
    return this.http.get<PaginaResponse<ProductoCatalogo>>(`${BASE_URL}/catalogo`, { params }).pipe(
      tap((data) => this._pagina.set(data)),
      catchError((err) => this.manejarError(err, this._error, 'No se pudo cargar el catálogo.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  // POST /api/productos — solo ADMINISTRADOR. El producto y sus presentaciones en una transacción.
  // 400 PRESENTACION_UNIDAD_REQUERIDA (no hay exactamente una de factor 1), 400 PRESENTACION_DUPLICADA,
  // 400 FORMATO_INVALIDO, 409 CODIGO_BARRAS_DUPLICADO.
  crear(request: ProductoRequest): Observable<ProductoCatalogo> {
    return this.guardar(this.http.post<ProductoCatalogo>(BASE_URL, request), 'No se pudo crear el producto.');
  }

  // PUT /api/productos/{id} — solo ADMINISTRADOR. Ver PresentacionRequest: las que no vienen se desactivan.
  // Además de los de crear: 400 FACTOR_NO_EDITABLE, 400 PRESENTACION_UNIDAD_REQUERIDA si se intenta
  // reemplazar la fila de factor 1, 400 PRESENTACION_INVALIDA (id de otro producto), 404 PRODUCTO_NO_ENCONTRADO.
  actualizar(id: number, request: ProductoRequest): Observable<ProductoCatalogo> {
    return this.guardar(this.http.put<ProductoCatalogo>(`${BASE_URL}/${id}`, request), 'No se pudo guardar el producto.');
  }

  // PATCH /api/productos/{id}/estado { activo } — solo ADMINISTRADOR. Nunca DELETE: un producto con ventas no se borra.
  // Desactivado: no sale en Punto de Venta ni en el alta de lote; sigue en Inventario, Alertas y Merma.
  cambiarEstado(id: number, activo: boolean): Observable<ProductoCatalogo> {
    return this.guardar(
      this.http.patch<ProductoCatalogo>(`${BASE_URL}/${id}/estado`, { activo }),
      'No se pudo cambiar el estado del producto.',
    );
  }

  limpiarErrorGuardado(): void {
    this._errorGuardado.set(null);
  }

  /** Molde de escritura: guardando/errorGuardado; el producto devuelto reemplaza su fila en la página cargada. */
  private guardar(peticion: Observable<ProductoCatalogo>, mensajeDefecto: string): Observable<ProductoCatalogo> {
    this._guardando.set(true);
    this._errorGuardado.set(null);
    return peticion.pipe(
      tap((producto) => this.reemplazarEnPagina(producto)),
      catchError((err) => this.manejarError(err, this._errorGuardado, mensajeDefecto)),
      finalize(() => this._guardando.set(false)),
    );
  }

  private reemplazarEnPagina(producto: ProductoCatalogo): void {
    const actual = this._pagina();
    if (actual && actual.contenido.some((p) => p.id === producto.id)) {
      this._pagina.set({ ...actual, contenido: actual.contenido.map((p) => (p.id === producto.id ? producto : p)) });
    }
  }

  /** Molde único de manejo de error (CLAUDE.md): guarda el mensaje traducido en el signal y vuelve a lanzar. */
  private manejarError(
    err: HttpErrorResponse & { traducido?: ErrorTraducido },
    destino: { set(valor: string | null): void },
    mensajeDefecto: string,
  ): Observable<never> {
    destino.set(err.traducido?.mensaje ?? mensajeDefecto);
    return throwError(() => err);
  }
}
