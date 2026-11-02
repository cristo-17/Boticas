import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { rolGuard } from './rol.guard';
import { AuthService } from '../services/auth.service';
import { Usuario } from '../models/usuario.model';

/** R17: la ruta de una pantalla de un solo rol tiene guard de rol, no solo de sesión. */
describe('rolGuard', () => {
  const usuario = signal<Usuario | null>(null);

  function correr(): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      rolGuard('ADMINISTRADOR')({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as boolean | UrlTree;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { usuarioActual: usuario } }],
    });
  });

  function conRol(rol: 'TECNICO' | 'ADMINISTRADOR'): void {
    usuario.set({ id: 1, nombre: 'X', usuario: 'x', rol, turno: 'Tarde', boticaNombre: 'B', boticaDireccion: 'D' });
  }

  it('deja pasar al ADMINISTRADOR', () => {
    conRol('ADMINISTRADOR');
    expect(correr()).toBe(true);
  });

  it('manda al TECNICO al Dashboard, no al login', () => {
    conRol('TECNICO');
    const resultado = correr();
    expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree)).toBe('/alertas');
  });

  it('sin usuario tampoco pasa', () => {
    usuario.set(null);
    expect(correr()).not.toBe(true);
  });
});
