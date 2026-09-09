import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { CredencialesLogin, LoginResponse, Usuario } from '../models/usuario.model';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/auth`;
const CLAVE_TOKEN = 'boticasys_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly _usuarioActual = signal<Usuario | null>(null);
  readonly usuarioActual = this._usuarioActual.asReadonly();
  readonly estaAutenticado = computed(() => this._usuarioActual() !== null);

  /**
   * NO es un signal a propósito: el interceptor lo lee de forma
   * síncrona en cada petición saliente, no necesita reactividad.
   * Persistencia (Tarea 12): localStorage sobrevive a recargar la
   * página — riesgo aceptado y explícito: un XSS en esta app podría
   * leerlo (no es httpOnly, a diferencia de una cookie). Mitigación
   * parcial: obtenerUsuarioActual() siempre revalida el token contra
   * el backend al arrancar, nunca confía en que "existe en localStorage"
   * signifique "sigue siendo válido".
   */
  private _token: string | null = localStorage.getItem(CLAVE_TOKEN);

  token(): string | null {
    return this._token;
  }

  // POST /api/auth/login
  iniciarSesion(credenciales: CredencialesLogin): Observable<Usuario> {
    return this.http.post<LoginResponse>(`${BASE_URL}/login`, credenciales).pipe(
      tap((respuesta) => this.guardarSesion(respuesta)),
      map((respuesta) => respuesta.usuario),
    );
  }

  // POST /api/auth/logout -- stateless (Tarea 12): igual se limpia la sesión local aunque la llamada falle (backend caído, token ya vencido).
  cerrarSesion(): Observable<void> {
    return this.http.post<void>(`${BASE_URL}/logout`, {}).pipe(
      tap(() => this.limpiarSesionLocal()),
      catchError(() => {
        this.limpiarSesionLocal();
        return of(undefined);
      }),
    );
  }

  // GET /api/auth/yo -- rehidrata la sesión al arrancar la app (Tarea 12). Sin token guardado, ni se llama.
  obtenerUsuarioActual(): Observable<Usuario | null> {
    if (!this._token) {
      return of(null);
    }
    return this.http.get<Usuario | null>(`${BASE_URL}/yo`).pipe(
      tap((usuario) => this._usuarioActual.set(usuario)),
      catchError(() => {
        this.limpiarSesionLocal();
        return of(null);
      }),
    );
  }

  /** Limpia la sesión SIN llamar al backend — la usa el interceptor de errores en un 401, para no arriesgar un loop si /logout también diera 401. */
  limpiarSesionLocal(): void {
    this._token = null;
    localStorage.removeItem(CLAVE_TOKEN);
    this._usuarioActual.set(null);
  }

  private guardarSesion(respuesta: LoginResponse): void {
    this._token = respuesta.token;
    localStorage.setItem(CLAVE_TOKEN, respuesta.token);
    this._usuarioActual.set(respuesta.usuario);
  }
}
