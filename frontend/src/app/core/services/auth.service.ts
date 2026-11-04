import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { CredencialesLogin, LoginResponse, Usuario } from '../models/usuario.model';
import { esFalloDeRed } from '../utils/red.util';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/auth`;
const CLAVE_TOKEN = 'boticasys_token';
/** Tarea 22: el usuario de la sesión, para abrir la app sin conexión (GET /auth/yo no responde). */
const CLAVE_USUARIO = 'boticasys_usuario';

/** Claims del JWT que el cliente usa (Tarea 22): dueño de la cola y vencimiento. */
export interface ClaimsToken {
  usuarioId: number;
  boticaId: number;
  /** segundos epoch */
  exp: number;
}

/** Lee los claims sin verificar la firma (eso lo hace el servidor): solo para decidir en el dispositivo. */
export function leerClaims(token: string | null): ClaimsToken | null {
  if (!token) return null;
  try {
    const carga = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = JSON.parse(decodeURIComponent(escape(atob(carga))));
    return { usuarioId: Number(json.usuarioId), boticaId: Number(json.boticaId), exp: Number(json.exp) };
  } catch {
    return null;
  }
}

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

  claims(): ClaimsToken | null {
    return leerClaims(this._token);
  }

  /**
   * Tarea 22 (§9): vender sin conexión exige una sesión vigente. Vencida, se
   * pide iniciar sesión, que necesita conexión. ahora: ms epoch (inyectable en tests).
   */
  sesionVigente(ahora = Date.now()): boolean {
    const c = this.claims();
    return c !== null && c.exp * 1000 > ahora;
  }

  // POST /api/auth/login
  iniciarSesion(credenciales: CredencialesLogin): Observable<Usuario> {
    return this.http.post<LoginResponse>(`${BASE_URL}/login`, credenciales).pipe(
      tap((respuesta) => this.guardarSesion(respuesta)),
      map((respuesta) => respuesta.usuario),
    );
  }

  // POST /api/auth/logout -- stateless: igual se limpia la sesión local aunque la llamada falle.
  // Tarea 22: la cola de ventas (IndexedDB) NO se toca al salir: se envía cuando su dueño vuelve a entrar.
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
  // Tarea 22: sin conexión (status 0 o 502-504) y con el token sin vencer, la sesión sigue con el
  // usuario guardado: el cajero puede abrir la app y vender sin señal. Con cualquier otra respuesta
  // de error (401: token inválido o usuario desactivado) se limpia, como antes.
  obtenerUsuarioActual(): Observable<Usuario | null> {
    if (!this._token) {
      return of(null);
    }
    return this.http.get<Usuario | null>(`${BASE_URL}/yo`).pipe(
      tap((usuario) => {
        this._usuarioActual.set(usuario);
        this.guardarUsuario(usuario);
      }),
      catchError((err) => {
        const guardado = this.usuarioGuardado();
        if (esFalloDeRed(err) && guardado && this.sesionVigente()) {
          this._usuarioActual.set(guardado);
          return of(guardado);
        }
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
      localStorage.removeItem(CLAVE_USUARIO);
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
    this.guardarUsuario(respuesta.usuario);
  }

  private guardarUsuario(usuario: Usuario | null): void {
    if (typeof localStorage === 'undefined') return;
    if (usuario) {
      localStorage.setItem(CLAVE_USUARIO, JSON.stringify(usuario));
    } else {
      localStorage.removeItem(CLAVE_USUARIO);
    }
  }

  private usuarioGuardado(): Usuario | null {
    try {
      const texto = typeof localStorage !== 'undefined' ? localStorage.getItem(CLAVE_USUARIO) : null;
      return texto ? (JSON.parse(texto) as Usuario) : null;
    } catch {
      return null;
    }
  }
}
