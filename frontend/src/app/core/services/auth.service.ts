import { Injectable, computed, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { tap } from 'rxjs/operators';
import { CredencialesLogin, Usuario } from '../models/usuario.model';
import { simulate } from './mock-utils';

const USUARIOS_MOCK: (Usuario & { password: string })[] = [
  {
    id: 'u1',
    nombre: 'Rosa Quispe',
    usuario: 'rosa.quispe',
    rol: 'Técnica farmacéutica',
    turno: 'Tarde',
    sede: 'Botica San Lucas · Av. Grau 412',
    password: '1234',
  },
];

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _usuarioActual = signal<Usuario | null>(null);
  readonly usuarioActual = this._usuarioActual.asReadonly();
  readonly estaAutenticado = computed(() => this._usuarioActual() !== null);

  // POST /api/auth/login
  iniciarSesion(credenciales: CredencialesLogin): Observable<Usuario> {
    const encontrado = USUARIOS_MOCK.find(
      (u) => u.usuario === credenciales.usuario && u.password === credenciales.password,
    );
    if (!encontrado) {
      return throwError(() => new Error('Usuario o contraseña incorrectos'));
    }
    const { password: _password, ...usuario } = encontrado;
    return simulate({ ...usuario, turno: credenciales.turno }, 700).pipe(
      tap((u) => this._usuarioActual.set(u)),
    );
  }

  // POST /api/auth/logout
  cerrarSesion(): Observable<void> {
    return simulate(undefined, 150).pipe(tap(() => this._usuarioActual.set(null)));
  }

  // GET /api/auth/yo — restaura la sesión al recargar la app.
  obtenerUsuarioActual(): Observable<Usuario | null> {
    return simulate(this._usuarioActual(), 150);
  }
}
