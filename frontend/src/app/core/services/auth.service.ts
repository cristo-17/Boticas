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
   * página.
   */
  private _token: string | null = typeof localStorage !== 'undefined' ? localStorage.getItem(CLAVE_TOKEN) : null;

  token(): string | null {
    return this._token;
  }

  /** Alias para compatibilidad con SSE / componentes que llamen obtenerToken() */
  obtenerToken(): string | null {
    return this.token();
  }

  // POST /api/auth/login
  iniciarSesion(credenciales: CredencialesLogin): Observable<Usuario> {
    return this.http.post<LoginResponse>(`${BASE_URL}/login`, credenciales).pipe(
      tap((respuesta) => this.guardarSesion(respuesta)),
      map((respuesta) => respuesta.usuario),
    );
  }

  // POST /api/auth/logout -- stateless: igual se limpia la sesión local aunque la llamada falle
  cerrarSesion(): Observable<void> {
    return this.http.post<void>(`${BASE_URL}/logout`, {}).pipe(
      tap(() => this.limpiarSesionLocal()),
      catchError(() => {
        this.limpiarSesionLocal();
        return of(undefined);
      }),
    );
  }

  // GET /api/auth/yo -- rehidrata la sesión al arrancar la app. Sin token guardado, ni se llama.
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

  /** Limpia la sesión SIN llamar al backend */
  limpiarSesionLocal(): void {
    this._token = null;
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(CLAVE_TOKEN);
    }
    this._usuarioActual.set(null);
  }

  /** Alias para compatibilidad con llamadas existentes a limpiarSesion() */
  limpiarSesion(): void {
    this.limpiarSesionLocal();
  }

  private guardarSesion(respuesta: LoginResponse): void {
    this._token = respuesta.token;
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(CLAVE_TOKEN, respuesta.token);
    }
    this._usuarioActual.set(respuesta.usuario);
  }
}
