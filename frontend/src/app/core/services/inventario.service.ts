import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import { FiltroLotes, Lote, NuevoLoteRequest } from '../models/lote.model';
import { PaginaResponse } from '../models/pagina.model';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/lotes`;

/** Categorías de referencia mientras no hay un catálogo separado — se completan con lo que traiga cada página cargada. */
const CATEGORIAS_INICIALES = ['Todas'];

@Injectable({ providedIn: 'root' })
export class InventarioService {
  private readonly http = inject(HttpClient);

  private readonly _pagina = signal<PaginaResponse<Lote> | null>(null);
  readonly pagina = this._pagina.asReadonly();

  /** La página actual, o [] mientras no haya cargado ninguna todavía. */
  readonly lotes = computed(() => this._pagina()?.contenido ?? []);

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  /** Regla de frontend (CLAUDE.md): toda pantalla que llama a este servicio muestra este signal. */
  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  private readonly _categorias = signal<string[]>(CATEGORIAS_INICIALES);
  readonly categorias = this._categorias.asReadonly();

  // GET /api/lotes?categoria=&vencimiento=&stockBajo=&productoId=&pagina=&tamano=&orden=
  listarLotes(filtro: FiltroLotes = {}, pagina = 0, tamano = 20, orden?: string): Observable<PaginaResponse<Lote>> {
    this._cargando.set(true);
    this._error.set(null);
    let params = new HttpParams().set('pagina', pagina).set('tamano', tamano);
    if (filtro.categoria) {
      params = params.set('categoria', filtro.categoria);
    }
    if (filtro.vencimiento) {
      params = params.set('vencimiento', filtro.vencimiento);
    }
    if (filtro.soloStockBajo) {
      params = params.set('stockBajo', true);
    }
    if (filtro.productoId !== undefined) {
      params = params.set('productoId', filtro.productoId);
    }
    if (orden) {
      params = params.set('orden', orden);
    }
    return this.http.get<PaginaResponse<Lote>>(BASE_URL, { params }).pipe(
      tap((data) => {
        this._pagina.set(data);
        this.actualizarCategorias(data.contenido);
      }),
      catchError((err) => this.manejarError(err, 'No se pudo cargar el inventario.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  // GET /api/lotes?productoId=&pagina=0&tamano=50 — segundo paso del flujo rediseñado de Merma (Tarea 11 Bloque C).
  listarLotesDeProducto(productoId: number): Observable<PaginaResponse<Lote>> {
    return this.listarLotes({ productoId }, 0, 50);
  }

  // POST /api/lotes
  registrarLote(request: NuevoLoteRequest): Observable<Lote> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http.post<Lote>(BASE_URL, request).pipe(
      catchError((err) => this.manejarError(err, 'No se pudo registrar el lote.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  /**
   * Lectura síncrona sobre la página actualmente cargada — usada por
   * MermaService (todavía mock, Tarea 11 Bloque C) para validar un
   * lote sin volver a pedirlo. Con HTTP real ya no hay "todos los
   * lotes" en memoria, solo la última página — mismo compromiso que
   * ProductoService.obtenerPorId. Bloque C reemplaza este uso por un
   * lookup real del backend.
   */
  obtenerLotePorId(loteId: number): Lote | undefined {
    return this.lotes().find((l) => l.id === loteId);
  }

  /**
   * Mutación local cosmética para que el mock de MermaService (Bloque C)
   * siga funcionando mientras no esté conectado a HTTP real — el
   * descuento de verdad lo hace el servidor dentro de POST /api/mermas
   * cuando ese bloque se construya. No es un endpoint propio.
   */
  descontarStock(loteId: number, cantidad: number): void {
    const actual = this._pagina();
    if (!actual) {
      return;
    }
    this._pagina.set({
      ...actual,
      contenido: actual.contenido.map((l) =>
        l.id === loteId ? { ...l, stock: Math.max(0, l.stock - cantidad) } : l,
      ),
    });
  }

  private actualizarCategorias(lotes: Lote[]): void {
    const nuevas = new Set(this._categorias());
    for (const l of lotes) {
      nuevas.add(l.categoria);
    }
    this._categorias.set(Array.from(nuevas));
  }

  /** Molde único de manejo de error (CLAUDE.md): guarda el mensaje traducido en el signal y vuelve a lanzar. */
  private manejarError(err: HttpErrorResponse & { traducido?: ErrorTraducido }, mensajeDefecto: string): Observable<never> {
    this._error.set(err.traducido?.mensaje ?? mensajeDefecto);
    return throwError(() => err);
  }
}
