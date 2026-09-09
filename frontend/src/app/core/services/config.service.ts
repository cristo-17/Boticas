import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { ConfigNegocio } from '../models/config.model';
import { environment } from '../../../environments/environment';

/**
 * Reemplaza las constantes que antes estaban hardcodeadas y duplicadas en
 * el cliente (TASA_IGV, UMBRAL_STOCK_BAJO, UMBRAL_DESCUADRE_LEVE,
 * MOTIVOS_MERMA...). Se consume UNA SOLA VEZ al iniciar la app
 * (app.config.ts, provideAppInitializer), no en cada pantalla — cualquier
 * otro servicio que necesite un valor de acá lo lee de `config()`.
 */
@Injectable({ providedIn: 'root' })
export class ConfigService {
  private readonly http = inject(HttpClient);

  private readonly _config = signal<ConfigNegocio | null>(null);
  readonly config = this._config.asReadonly();

  // GET /api/config
  cargar(): Observable<ConfigNegocio> {
    return this.http
      .get<ConfigNegocio>(`${environment.apiUrl}/config`)
      .pipe(tap((data) => this._config.set(data)));
  }
}
