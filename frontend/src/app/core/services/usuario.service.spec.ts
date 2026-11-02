import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { UsuarioService } from './usuario.service';
import { PaginaResponse } from '../models/pagina.model';
import { UsuarioAdmin } from '../models/usuario.model';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/usuarios`;

function usuario(id: number, overrides: Partial<UsuarioAdmin> = {}): UsuarioAdmin {
  return {
    id,
    nombre: `Usuario ${id}`,
    usuario: `user.${id}`,
    rol: 'TECNICO',
    activo: true,
    intentosFallidos: 0,
    bloqueadoHasta: null,
    bloqueado: false,
    tieneCajaAbierta: false,
    creadoEn: '2026-11-02T10:00:00-05:00',
    ...overrides,
  };
}

function pagina(...contenido: UsuarioAdmin[]): PaginaResponse<UsuarioAdmin> {
  return { contenido, pagina: 0, tamano: 20, totalElementos: contenido.length, totalPaginas: 1 };
}

describe('UsuarioService', () => {
  let service: UsuarioService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(UsuarioService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listar hace GET a /usuarios con buscar, pagina y tamano, y guarda la página', () => {
    service.listar('rosa', 0, 20).subscribe();

    const req = http.expectOne((r) => r.url === BASE_URL);
    expect(req.request.params.get('buscar')).toBe('rosa');
    expect(service.cargando()).toBe(true);
    req.flush(pagina(usuario(1)));

    expect(service.usuarios().map((u) => u.id)).toEqual([1]);
    expect(service.cargando()).toBe(false);
  });

  it('crear hace POST y un error queda en errorGuardado sin pisar el error del listado', () => {
    service
      .crear({ nombre: 'Rosa', usuario: 'rosa.quispe', password: 'claveSegura123', rol: 'TECNICO' })
      .subscribe({ error: () => {} });

    const req = http.expectOne(BASE_URL);
    expect(req.request.method).toBe('POST');
    expect(service.guardando()).toBe(true);
    req.flush(
      { error: 'USUARIO_DUPLICADO', mensaje: 'El usuario rosa.quispe ya está en uso' },
      { status: 409, statusText: 'Conflict' },
    );

    expect(service.guardando()).toBe(false);
    expect(service.errorGuardado()).not.toBeNull();
    expect(service.error()).toBeNull();
  });

  it('actualizar, restablecerClave y desbloquear reemplazan la fila en la página cargada', () => {
    service.listar().subscribe();
    http.expectOne((r) => r.url === BASE_URL).flush(pagina(usuario(1, { bloqueado: true }), usuario(2)));

    service.desbloquear(1).subscribe();
    const reqDesbloq = http.expectOne(`${BASE_URL}/1/desbloquear`);
    expect(reqDesbloq.request.method).toBe('POST');
    reqDesbloq.flush(usuario(1, { bloqueado: false }));
    expect(service.usuarios().find((u) => u.id === 1)?.bloqueado).toBe(false);

    service.actualizar(2, { nombre: 'Usuario 2', rol: 'TECNICO', activo: false }).subscribe();
    const reqPatch = http.expectOne(`${BASE_URL}/2`);
    expect(reqPatch.request.method).toBe('PATCH');
    reqPatch.flush(usuario(2, { activo: false }));
    expect(service.usuarios().find((u) => u.id === 2)?.activo).toBe(false);

    service.restablecerClave(1, 'nuevaClave123').subscribe();
    const reqClave = http.expectOne(`${BASE_URL}/1/restablecer-clave`);
    expect(reqClave.request.method).toBe('POST');
    expect(reqClave.request.body).toEqual({ password: 'nuevaClave123' });
    reqClave.flush(usuario(1));
  });
});
