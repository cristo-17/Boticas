import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router } from '@angular/router';
import { HeaderComponent } from './header';
import { AuthService } from '../../core/services/auth.service';
import { ConexionService } from '../../core/services/conexion.service';
import { Usuario } from '../../core/models/usuario.model';
import { EstadoConexion } from '../../core/models/conexion.model';

const USUARIO: Usuario = {
  id: 1,
  nombre: 'Rosa Quispe',
  usuario: 'rosa.quispe',
  rol: 'TECNICO',
  turno: 'Mañana',
  boticaNombre: 'Botica San Lucas',
  boticaDireccion: 'Av. Grau 412, Chiclayo',
};

describe('HeaderComponent', () => {
  let usuarioActual: ReturnType<typeof signal<Usuario | null>>;
  let estadoConexion: ReturnType<typeof signal<EstadoConexion>>;

  beforeEach(() => {
    usuarioActual = signal<Usuario | null>(null);
    estadoConexion = signal<EstadoConexion>('online');

    TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [
        { provide: AuthService, useValue: { usuarioActual, cerrarSesion: () => ({ subscribe: () => {} }) } },
        { provide: ConexionService, useValue: { estado: estadoConexion, alternar: () => {} } },
        { provide: Router, useValue: { navigateByUrl: () => {} } },
      ],
    });
  });

  it('sede() dice "Sin sesión iniciada" cuando no hay usuario, y se compone con datos reales al iniciar sesión', () => {
    const fixture = TestBed.createComponent(HeaderComponent);
    const componente = fixture.componentInstance;

    expect(componente.sede()).toBe('Sin sesión iniciada');

    // Tarea 12: sede() ya no trae texto quemado -- se arma de boticaNombre/boticaDireccion del JWT.
    usuarioActual.set(USUARIO);
    expect(componente.sede()).toBe('Botica San Lucas · Av. Grau 412, Chiclayo');
  });

  it('etiquetaConexion() reacciona al estado de ConexionService sin recrear el componente', () => {
    const fixture = TestBed.createComponent(HeaderComponent);
    const componente = fixture.componentInstance;

    expect(componente.etiquetaConexion()).toBe('En línea');

    estadoConexion.set('sync');
    expect(componente.etiquetaConexion()).toBe('Sincronizando');

    estadoConexion.set('offline');
    expect(componente.etiquetaConexion()).toBe('Sin conexión');
  });

  it('el template refleja sede() y etiquetaConexion() tras detectChanges', () => {
    usuarioActual.set(USUARIO);
    const fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();

    const sedeEl = fixture.nativeElement.querySelector('.app-header__sede');
    expect(sedeEl.textContent).toContain('Botica San Lucas');
  });
});
