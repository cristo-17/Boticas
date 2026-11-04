import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { SidebarNavComponent } from './sidebar-nav';
import { AuthService } from '../../core/services/auth.service';
import { CajaService } from '../../core/services/caja.service';
import { ConexionService } from '../../core/services/conexion.service';
import { AlertaService } from '../../core/services/alerta.service';

/** RF-03: "Cerrar sesión" del menú lateral cierra la sesión y lleva a /login. */
describe('SidebarNavComponent — cerrar sesión', () => {
  it('el botón llama a cerrarSesion() y navega a /login', async () => {
    let cierres = 0;
    TestBed.configureTestingModule({
      imports: [SidebarNavComponent],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            usuarioActual: signal({ nombre: 'Rosa Quispe', rol: 'TECNICO', turno: 'Tarde' }),
            cerrarSesion: () => (cierres++, of(undefined)),
          },
        },
        { provide: CajaService, useValue: { cajaActual: signal(null) } },
        { provide: ConexionService, useValue: { estado: signal('online') } },
        { provide: AlertaService, useValue: { alertas: signal([]) } },
      ],
    });
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(SidebarNavComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const boton = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Cerrar sesión'),
    ) as HTMLButtonElement;
    boton.click();

    expect(cierres).toBe(1);
    expect(navegar).toHaveBeenCalledWith('/login');
  });
});
