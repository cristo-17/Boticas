import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router } from '@angular/router';
import { HeaderComponent } from './header';
import { AuthService } from '../../core/services/auth.service';
import { ConexionService } from '../../core/services/conexion.service';
import { Usuario } from '../../core/models/usuario.model';
import { EstadoConexion } from '../../core/models/conexion.model';
import { ColaVentasService } from '../../core/services/cola-ventas.service';
import { catalogoFalso, colaFalsa } from './cola-falsa.testing';
import { CatalogoOfflineService } from '../../core/services/catalogo-offline.service';
import { ConfigService } from '../../core/services/config.service';

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
        { provide: ConexionService, useValue: { estado: estadoConexion, enLinea: () => true, comprobar: async () => true } },
        { provide: ColaVentasService, useValue: colaFalsa() },
        { provide: CatalogoOfflineService, useValue: catalogoFalso() },
        { provide: ConfigService, useValue: { config: signal(null) } },
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

  it('el template refleja sede() tras detectChanges', () => {
    usuarioActual.set(USUARIO);
    const fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();

    const sedeEl = fixture.nativeElement.querySelector('.app-header__sede');
    expect(sedeEl.textContent).toContain('Botica San Lucas');
  });

  it('E3: una botica sin dirección muestra solo su nombre, nunca "· null"', () => {
    usuarioActual.set({ ...USUARIO, boticaNombre: 'Charito', boticaDireccion: null });
    const fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.sede()).toBe('Charito');
    const sedeEl = fixture.nativeElement.querySelector('.app-header__sede') as HTMLElement;
    expect(sedeEl.textContent?.trim()).toBe('Charito');
    expect(sedeEl.textContent).not.toContain('null');
  });

  it('E2: el chip de conexión va en modo compacto (texto oculto solo debajo de 1024 px) y "Salir" existe con texto y como ícono', () => {
    usuarioActual.set(USUARIO);
    const fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const chip = el.querySelector('.bs-conn') as HTMLElement;
    expect(chip.classList).toContain('bs-conn--compacto-movil');
    expect(chip.querySelector('.bs-conn__texto')?.textContent).toContain('En línea'); // sigue para lectores de pantalla
    expect(el.querySelector('.app-header__salir-texto')?.textContent).toContain('Salir');
    expect(el.querySelector('.app-header__salir-icono button')?.getAttribute('aria-label')).toBe('Salir');
  });
});
