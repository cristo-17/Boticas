import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { CredencialesLogin, LoginResponse, Usuario } from '../models/usuario.model';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/auth`;
const TOKEN_KEY = 'boticasys_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly _usuarioActual = signal<Usuario | null>(null);
  readonly usuarioActual = this._usuarioActual.asReadonly();
  readonly estaAutenticado = computed(() => this._usuarioActual() !== null);

  // POST /api/auth/login
  iniciarSesion(credenciales: CredencialesLogin): Observable<Usuario> {
    return this.http.post<LoginResponse>(`${BASE_URL}/login`, credenciales).pipe(
      tap((res) => {
        this.guardarToken(res.token);
        this._usuarioActual.set(res.usuario);
      }),
      map((res) => res.usuario),
    );
  }

  limpiarSesion(): void {
    this.removerToken();
    this._usuarioActual.set(null);
  }

  // POST /api/auth/logout
  cerrarSesion(): Observable<void> {
    return this.http.post<void>(`${BASE_URL}/logout`, {}).pipe(
      catchError(() => of(undefined as void)),
      tap(() => this.limpiarSesion()),
    );
  }

  // GET /api/auth/yo — restaura la sesión al recargar la app.
  obtenerUsuarioActual(): Observable<Usuario | null> {
    const token = this.obtenerToken();
    if (!token) {
      this._usuarioActual.set(null);
      return of(null);
    }
    return this.http.get<Usuario>(`${BASE_URL}/yo`).pipe(
      tap((usuario) => this._usuarioActual.set(usuario)),
      catchError(() => {
        this.limpiarSesion();
        return of(null);
      }),
    );
  }

  obtenerToken(): string | null {
    if (typeof sessionStorage !== 'undefined') {
      return sessionStorage.getItem(TOKEN_KEY);
    }
    return null;
  }

  private guardarToken(token: string): void {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.setItem(TOKEN_KEY, token);
    }
  }

  private removerToken(): void {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.removeItem(TOKEN_KEY);
    }
  }
}
