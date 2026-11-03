import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { Kardex } from '../models/kardex.model';

export interface FiltroKardex {
  productoId: number;
  /** YYYY-MM-DD (día de Lima); vacío = desde el primer movimiento. */
  desde?: string;
  /** YYYY-MM-DD (día de Lima), inclusive; vacío = hasta hoy. */
  hasta?: string;
}

/**
 * Kardex por producto — solo ADMINISTRADOR (403 SIN_PERMISO para TECNICO).
 *
 * Contrato (docs/API-CONTRATO.md, Tarea 20):
 * - GET /api/kardex?productoId=&desde=YYYY-MM-DD&hasta=YYYY-MM-DD&pagina=&tamano=
 *   → Kardex. productoId obligatorio; desde y hasta opcionales (días de Lima).
 *   Con desde, la primera fila de la primera página es SALDO_ANTERIOR y el saldo
 *   sigue desde ahí. Paginado (tamano por defecto 50, máximo 100); el saldo de
 *   cualquier página ya viene calculado.
 *   400 FILTRO_INVALIDO (sin productoId o no numérico), 400 RANGO_FECHAS_INVALIDO
 *   (fecha mal escrita o desde > hasta), 404 PRODUCTO_NO_ENCONTRADO (inexistente
 *   o de otra botica). Un producto desactivado conserva su Kardex.
 * - GET /api/kardex/xlsx?productoId=&desde=&hasta=
 *   → application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, todas
 *     las filas sin paginar, archivo kardex_<productoId>[_<desde>][_<hasta>].xlsx.
 */
@Injectable({ providedIn: 'root' })
export class KardexService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/kardex`;

  private readonly _kardex = signal<Kardex | null>(null);
  private readonly _cargando = signal(false);
  private readonly _descargandoExcel = signal(false);
  /** Regla de frontend (CLAUDE.md): toda pantalla que llama a este servicio muestra este signal. */
  private readonly _error = signal<string | null>(null);

  readonly kardex = this._kardex.asReadonly();
  readonly cargando = this._cargando.asReadonly();
  readonly descargandoExcel = this._descargandoExcel.asReadonly();
  readonly error = this._error.asReadonly();

  consultar(filtro: FiltroKardex, pagina = 0, tamano = 50): Observable<Kardex> {
    this._cargando.set(true);
    this._error.set(null);
    const params = this.params(filtro).set('pagina', pagina).set('tamano', tamano);
    return this.http.get<Kardex>(this.apiUrl, { params }).pipe(
      tap((k) => this._kardex.set(k)),
      catchError((err) => this.capturarError(err, 'No se pudo cargar el Kardex.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  descargarExcel(filtro: FiltroKardex): Observable<Blob> {
    this._descargandoExcel.set(true);
    this._error.set(null);
    return this.http
      .get(`${this.apiUrl}/xlsx`, { params: this.params(filtro), responseType: 'blob' })
      .pipe(
        catchError((err) => this.capturarError(err, 'No se pudo descargar el Excel del Kardex.')),
        finalize(() => this._descargandoExcel.set(false)),
      );
  }

  /** Al cambiar de producto, la tabla anterior no debe quedar a la vista. */
  limpiar(): void {
    this._kardex.set(null);
    this._error.set(null);
  }

  /** Mismo nombre que manda el servidor en Content-Disposition. */
  static nombreArchivoExcel(filtro: FiltroKardex): string {
    return `kardex_${filtro.productoId}${filtro.desde ? '_' + filtro.desde : ''}${filtro.hasta ? '_' + filtro.hasta : ''}.xlsx`;
  }

  private params(filtro: FiltroKardex): HttpParams {
    let params = new HttpParams().set('productoId', filtro.productoId);
    if (filtro.desde) params = params.set('desde', filtro.desde);
    if (filtro.hasta) params = params.set('hasta', filtro.hasta);
    return params;
  }

  private capturarError(err: HttpErrorResponse & { traducido?: ErrorTraducido }, porDefecto: string): Observable<never> {
    this._error.set(err.traducido?.mensaje ?? porDefecto);
    return throwError(() => err);
  }
}
