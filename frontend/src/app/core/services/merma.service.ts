import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import { Merma, MotivoMerma, NuevaMermaRequest } from '../models/merma.model';
import { PaginaResponse } from '../models/pagina.model';
import { ConfigService } from './config.service';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/mermas`;

/** Fallback si /api/config no llegó a cargar todavía (no debería pasar: provideAppInitializer la espera antes de arrancar la app). */
const MOTIVOS_QUE_REQUIEREN_OBSERVACION_DEFECTO: MotivoMerma[] = ['Robo o pérdida', 'Otro'];
const MOTIVOS_MERMA_DEFECTO: MotivoMerma[] = ['Vencimiento', 'Rotura', 'Deterioro', 'Robo o pérdida', 'Otro'];

@Injectable({ providedIn: 'root' })
export class MermaService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ConfigService);

  private readonly _mermas = signal<Merma[]>([]);
  readonly mermas = this._mermas.asReadonly();

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  /** Regla de frontend (CLAUDE.md): toda pantalla que llama a este servicio muestra este signal. */
  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  /** 'Robo o pérdida' y 'Otro' exigen observación por defecto — se valida también en el backend, nunca solo acá. */
  get motivosMerma(): MotivoMerma[] {
    return (this.config.config()?.motivosMerma as MotivoMerma[] | undefined) ?? MOTIVOS_MERMA_DEFECTO;
  }

  get motivosQueRequierenObservacion(): MotivoMerma[] {
    return (
      (this.config.config()?.motivosQueRequierenObservacion as MotivoMerma[] | undefined) ??
      MOTIVOS_QUE_REQUIEREN_OBSERVACION_DEFECTO
    );
  }

  // GET /api/mermas?fecha=hoy
  listarMermasDelDia(): Observable<PaginaResponse<Merma>> {
    this._cargando.set(true);
    this._error.set(null);
    const params = new HttpParams().set('fecha', 'hoy');
    return this.http.get<PaginaResponse<Merma>>(BASE_URL, { params }).pipe(
      tap((data) => this._mermas.set(data.contenido)),
      catchError((err) => this.manejarError(err, 'No se pudieron cargar las mermas del día.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  // POST /api/mermas — la cantidad topada al stock real y la observación obligatoria las valida EL SERVIDOR (Regla 8): es destructiva y sin deshacer.
  registrarMerma(request: NuevaMermaRequest): Observable<Merma> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http.post<Merma>(BASE_URL, request).pipe(
      tap((merma) => this._mermas.update((all) => [merma, ...all])),
      catchError((err) => this.manejarError(err, 'No se pudo registrar la merma.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  /** Molde único de manejo de error (CLAUDE.md): guarda el mensaje traducido en el signal y vuelve a lanzar. */
  private manejarError(err: HttpErrorResponse & { traducido?: ErrorTraducido }, mensajeDefecto: string): Observable<never> {
    this._error.set(err.traducido?.mensaje ?? mensajeDefecto);
    return throwError(() => err);
  }
}
