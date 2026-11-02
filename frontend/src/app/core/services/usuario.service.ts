import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { PaginaResponse } from '../models/pagina.model';
import { ActualizarUsuarioRequest, CrearUsuarioRequest, UsuarioAdmin } from '../models/usuario.model';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/usuarios`;

/**
 * Administración de usuarios de la botica, solo ADMINISTRADOR (Tarea 17, Bloque B; R17).
 * Sigue el mismo molde que CatalogoService: estado del listado (cargando/error) y estado
 * de operaciones de escritura (guardando/errorGuardado) separados.
 */
@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);

  private readonly _pagina = signal<PaginaResponse<UsuarioAdmin> | null>(null);
  readonly pagina = this._pagina.asReadonly();
  readonly usuarios = computed(() => this._pagina()?.contenido ?? []);

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  private readonly _guardando = signal(false);
  readonly guardando = this._guardando.asReadonly();

  private readonly _errorGuardado = signal<string | null>(null);
  readonly errorGuardado = this._errorGuardado.asReadonly();

  // GET /api/usuarios?buscar=&pagina=&tamano=&orden= — PAGINADO, solo ADMINISTRADOR.
  // Activos e inactivos de la botica. orden: nombre|usuario|rol (,asc|,desc).
  listar(buscar = '', pagina = 0, tamano = 20, orden?: string): Observable<PaginaResponse<UsuarioAdmin>> {
    this._cargando.set(true);
    this._error.set(null);
    let params = new HttpParams().set('buscar', buscar).set('pagina', pagina).set('tamano', tamano);
    if (orden) {
      params = params.set('orden', orden);
    }
    return this.http.get<PaginaResponse<UsuarioAdmin>>(BASE_URL, { params }).pipe(
      tap((data) => this._pagina.set(data)),
      catchError((err) => this.manejarError(err, this._error, 'No se pudo cargar la lista de usuarios.')),
      finalize(() => this._cargando.set(false)),
    );
  }

  // POST /api/usuarios — solo ADMINISTRADOR. Contraseña mínimo 8 caracteres; usuario único global.
  // 400 FORMATO_INVALIDO, 409 USUARIO_DUPLICADO.
  crear(request: CrearUsuarioRequest): Observable<UsuarioAdmin> {
    return this.guardar(this.http.post<UsuarioAdmin>(BASE_URL, request), 'No se pudo crear el usuario.');
  }

  // PATCH /api/usuarios/{id} { nombre, rol, activo } — solo ADMINISTRADOR.
  // 409 AUTO_MODIFICACION_ADMIN si intenta desactivarse o quitarse el rol a sí mismo.
  actualizar(id: number, request: ActualizarUsuarioRequest): Observable<UsuarioAdmin> {
    return this.guardar(this.http.patch<UsuarioAdmin>(`${BASE_URL}/${id}`, request), 'No se pudo actualizar el usuario.');
  }

  // POST /api/usuarios/{id}/restablecer-clave { password } — solo ADMINISTRADOR.
  // También reinicia intentos_fallidos = 0 y bloqueado_hasta = null.
  restablecerClave(id: number, password: string): Observable<UsuarioAdmin> {
    return this.guardar(
      this.http.post<UsuarioAdmin>(`${BASE_URL}/${id}/restablecer-clave`, { password }),
      'No se pudo restablecer la contraseña.',
    );
  }

  // POST /api/usuarios/{id}/desbloquear — solo ADMINISTRADOR.
  desbloquear(id: number): Observable<UsuarioAdmin> {
    return this.guardar(
      this.http.post<UsuarioAdmin>(`${BASE_URL}/${id}/desbloquear`, {}),
      'No se pudo desbloquear la cuenta.',
    );
  }

  limpiarErrorGuardado(): void {
    this._errorGuardado.set(null);
  }

  private guardar(peticion: Observable<UsuarioAdmin>, mensajeDefecto: string): Observable<UsuarioAdmin> {
    this._guardando.set(true);
    this._errorGuardado.set(null);
    return peticion.pipe(
      tap((usuario) => this.reemplazarEnPagina(usuario)),
      catchError((err) => this.manejarError(err, this._errorGuardado, mensajeDefecto)),
      finalize(() => this._guardando.set(false)),
    );
  }

  private reemplazarEnPagina(usuario: UsuarioAdmin): void {
    const actual = this._pagina();
    if (actual && actual.contenido.some((u) => u.id === usuario.id)) {
      this._pagina.set({
        ...actual,
        contenido: actual.contenido.map((u) => (u.id === usuario.id ? usuario : u)),
      });
    }
  }

  private manejarError(
    err: HttpErrorResponse & { traducido?: ErrorTraducido },
    destino: { set(valor: string | null): void },
    mensajeDefecto: string,
  ): Observable<never> {
    destino.set(err.traducido?.mensaje ?? mensajeDefecto);
    return throwError(() => err);
  }
}
