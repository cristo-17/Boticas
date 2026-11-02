import { TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { BottomNavComponent } from './bottom-nav';
import { AuthService } from '../../core/services/auth.service';
import { Rol, Usuario } from '../../core/models/usuario.model';

@Component({ template: '' })
class PantallaVacia {}

describe('BottomNavComponent', () => {
  const usuario = signal<Usuario | null>(null);

  function conRol(rol: Rol): void {
    usuario.set({ id: 1, nombre: 'X', usuario: 'x', rol, turno: 'Tarde', boticaNombre: 'B', boticaDireccion: 'D' });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BottomNavComponent],
      providers: [provideRouter([{ path: '**', component: PantallaVacia }]), { provide: AuthService, useValue: { usuarioActual: usuario } }],
    });
  });

  function crear() {
    const fixture = TestBed.createComponent(BottomNavComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('ADMINISTRADOR: 5 accesos en la barra (4 + "Más"), y "Más" abre Catálogo', () => {
    conRol('ADMINISTRADOR');
    const fixture = crear();
    const el: HTMLElement = fixture.nativeElement;

    const accesos = el.querySelectorAll(':scope > .bottom-nav__item');
    expect(accesos.length).toBe(5);
    expect(el.querySelector('#bottom-nav-mas')).toBeNull();

    (accesos[4] as HTMLButtonElement).click();
    fixture.detectChanges();

    const panel = el.querySelector('#bottom-nav-mas');
    expect(panel?.textContent).toContain('Catálogo');
    expect(accesos[4].getAttribute('aria-expanded')).toBe('true');
  });

  it('elegir una sección de "Más" cierra el panel', async () => {
    conRol('ADMINISTRADOR');
    const fixture = crear();
    const el: HTMLElement = fixture.nativeElement;
    (el.querySelectorAll(':scope > .bottom-nav__item')[4] as HTMLButtonElement).click();
    fixture.detectChanges();

    (el.querySelector('.bottom-nav__mas-item') as HTMLAnchorElement).click();
    await fixture.whenStable(); // el routerLink navega de forma asíncrona
    fixture.detectChanges();

    expect(el.querySelector('#bottom-nav-mas')).toBeNull();
  });

  it('TECNICO: tampoco pasa de 5, y no ve Catálogo en "Más"', () => {
    conRol('TECNICO');
    const fixture = crear();
    const el: HTMLElement = fixture.nativeElement;
    const accesos = el.querySelectorAll(':scope > .bottom-nav__item');
    expect(accesos.length).toBe(5);

    (accesos[4] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('#bottom-nav-mas')?.textContent).not.toContain('Catálogo');
  });
});
