import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import { ConfigNegocio } from '../models/config.model';
import { environment } from '../../../environments/environment';

/**
 * Reemplaza las constantes que antes estaban hardcodeadas y duplicadas en
 * el cliente (TASA_IGV, UMBRAL_STOCK_BAJO, UMBRAL_DESCUADRE_LEVE,
 * MOTIVOS_MERMA...). Se consume UNA VEZ al iniciar la app
 * (app.config.ts, provideAppInitializer), no en cada pantalla — cualquier
 * otro servicio que necesite un valor de acá lo lee de `config()`. Si
 * falla, el Shell la vuelve a pedir después del login (DIF-03).
 */
@Injectable({ providedIn: 'root' })
export class ConfigService {
  private readonly http = inject(HttpClient);

  private readonly _config = signal<ConfigNegocio | null>(null);
  readonly config = this._config.asReadonly();

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  /** Lo muestra el Shell (DIF-03): mientras falle, la app sigue con los valores por defecto de cada servicio. */
  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  // GET /api/config
  cargar(): Observable<ConfigNegocio> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http.get<ConfigNegocio>(`${environment.apiUrl}/config`).pipe(
      tap((data) => this._config.set(data)),
      catchError((err) => {
        this._error.set(
          'No se pudo cargar la configuración del negocio. Se usan los valores por defecto (IGV 18 %, umbrales estándar) hasta que vuelvas a iniciar sesión.',
        );
        return throwError(() => err);
      }),
      finalize(() => this._cargando.set(false)),
    );
  }
}
