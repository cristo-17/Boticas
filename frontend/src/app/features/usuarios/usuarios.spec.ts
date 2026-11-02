import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { UsuariosScreen } from './usuarios';
import { errorInterceptor } from '../../core/interceptors/error.interceptor';
import { AuthService } from '../../core/services/auth.service';
import { PaginaResponse } from '../../core/models/pagina.model';
import { Usuario, UsuarioAdmin } from '../../core/models/usuario.model';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/usuarios`;

const ADMIN: UsuarioAdmin = {
  id: 2,
  nombre: 'Carlos Mendoza',
  usuario: 'carlos.mendoza',
  rol: 'ADMINISTRADOR',
  activo: true,
  intentosFallidos: 0,
  bloqueadoHasta: null,
  bloqueado: false,
  tieneCajaAbierta: false,
  creadoEn: '2026-11-02T08:00:00-05:00',
};

const TECNICO_CON_CAJA: UsuarioAdmin = {
  id: 1,
  nombre: 'Rosa Quispe',
  usuario: 'rosa.quispe',
  rol: 'TECNICO',
  activo: true,
  intentosFallidos: 5,
  bloqueadoHasta: '2026-11-02T15:15:00Z',
  bloqueado: true,
  tieneCajaAbierta: true,
  creadoEn: '2026-11-02T08:00:00-05:00',
};

function pagina(contenido: UsuarioAdmin[]): PaginaResponse<UsuarioAdmin> {
  return { contenido, pagina: 0, tamano: 20, totalElementos: contenido.length, totalPaginas: 1 };
}

describe('UsuariosScreen', () => {
  let http: HttpTestingController;
  const usuarioActual = signal<Usuario | null>({
    id: 2,
    nombre: 'Carlos Mendoza',
    usuario: 'carlos.mendoza',
    rol: 'ADMINISTRADOR',
    turno: 'Mañana',
    boticaNombre: 'Botica San Lucas',
    boticaDireccion: 'Av. Grau 412',
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [UsuariosScreen],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            usuarioActual: usuarioActual.asReadonly(),
            limpiarSesionLocal: () => {},
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function crear() {
    const fixture = TestBed.createComponent(UsuariosScreen);
    fixture.detectChanges();
    await fixture.whenStable();
    http.expectOne((r) => r.url === BASE_URL).flush(pagina([ADMIN, TECNICO_CON_CAJA]));
    fixture.detectChanges();
    return fixture;
  }

  it('desactivar a un técnico con caja abierta pide confirmación avisando que el admin debe cerrar su caja', async () => {
    const fixture = await crear();
    const pantalla = fixture.componentInstance;

    pantalla.solicitarCambioEstado(TECNICO_CON_CAJA);
    fixture.detectChanges();

    expect(pantalla.confirmarDesactivar()?.id).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Rosa Quispe tiene una caja abierta');
    http.expectNone(`${BASE_URL}/1`);

    pantalla.confirmarDesactivacionConCaja();
    const req = http.expectOne(`${BASE_URL}/1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ nombre: 'Rosa Quispe', rol: 'TECNICO', activo: false });
    req.flush({ ...TECNICO_CON_CAJA, activo: false });
  });

  it('desbloquear llama a POST /usuarios/{id}/desbloquear y quita el estado bloqueado', async () => {
    const fixture = await crear();
    const pantalla = fixture.componentInstance;

    expect(fixture.nativeElement.textContent).toContain('Bloqueado hasta las 10:15');
    pantalla.desbloquear(TECNICO_CON_CAJA);

    const req = http.expectOne(`${BASE_URL}/1/desbloquear`);
    expect(req.request.method).toBe('POST');
    req.flush({ ...TECNICO_CON_CAJA, bloqueado: false, bloqueadoHasta: null, intentosFallidos: 0 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Bloqueado hasta las');
  });

  it('nuevo usuario valida contraseña de al menos 8 caracteres antes de llamar al servidor', async () => {
    const fixture = await crear();
    const pantalla = fixture.componentInstance;

    pantalla.abrirNuevo();
    pantalla.nombre.set('Lucía Gómez');
    pantalla.usuarioLogin.set('lucia.gomez');
    pantalla.password.set('corta');
    pantalla.guardar();
    fixture.detectChanges();

    http.expectNone(BASE_URL);
    expect(fixture.nativeElement.querySelector('.usuarios__errores')?.textContent).toContain('al menos 8 caracteres');
  });
});
