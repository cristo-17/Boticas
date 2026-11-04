import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { Usuario } from '../models/usuario.model';
import { environment } from '../../../environments/environment';

const URL = `${environment.apiUrl}/auth`;
const USUARIO: Usuario = {
  id: 3, nombre: 'Rosa Quispe', usuario: 'rosa.quispe', rol: 'TECNICO', turno: 'Tarde',
  boticaNombre: 'Botica San Lucas', boticaDireccion: null,
};

/** Un JWT con la forma del servidor (la firma no se verifica en el cliente). */
function token(expSegundos: number): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${b64({ alg: 'HS256' })}.${b64({ usuarioId: 3, boticaId: 1, exp: expSegundos })}.firma`;
}

/** RF-02 (rehidratación) y RF-03 (cierre de sesión) del lado de Angular. */
describe('AuthService — sesión guardada', () => {
  let http: HttpTestingController;

  function crear(): AuthService {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('sin token guardado no llama a /yo y no hay sesión', () => {
    const auth = crear();
    let resultado: Usuario | null | undefined;
    auth.obtenerUsuarioActual().subscribe((u) => (resultado = u));
    http.expectNone(`${URL}/yo`);
    expect(resultado).toBeNull();
    expect(auth.estaAutenticado()).toBe(false);
  });

  it('con token guardado (recargar la página): pide /yo con ese token y repone al usuario sin pedir credenciales', () => {
    localStorage.setItem('boticasys_token', token(Date.now() / 1000 + 3600));
    const auth = crear();
    expect(auth.token()).not.toBeNull();
    auth.obtenerUsuarioActual().subscribe();
    http.expectOne(`${URL}/yo`).flush(USUARIO);
    expect(auth.usuarioActual()).toEqual(USUARIO);
    expect(auth.estaAutenticado()).toBe(true);
    expect(auth.claims()).toEqual(expect.objectContaining({ usuarioId: 3, boticaId: 1 }));
  });

  it('/yo responde 401 (token vencido, usuario desactivado o rol cambiado): borra la sesión guardada', () => {
    localStorage.setItem('boticasys_token', token(Date.now() / 1000 + 3600));
    const auth = crear();
    let resultado: Usuario | null | undefined;
    auth.obtenerUsuarioActual().subscribe((u) => (resultado = u));
    http.expectOne(`${URL}/yo`).flush({ error: 'NO_AUTENTICADO' }, { status: 401, statusText: 'Unauthorized' });
    expect(resultado).toBeNull();
    expect(auth.token()).toBeNull();
    expect(localStorage.getItem('boticasys_token')).toBeNull();
  });

  it('sin red, con el token vigente y el usuario guardado: la sesión sigue (Tarea 22)', () => {
    localStorage.setItem('boticasys_token', token(Date.now() / 1000 + 3600));
    localStorage.setItem('boticasys_usuario', JSON.stringify(USUARIO));
    const auth = crear();
    auth.obtenerUsuarioActual().subscribe();
    http.expectOne(`${URL}/yo`).error(new ProgressEvent('error'), { status: 0 });
    expect(auth.usuarioActual()?.nombre).toBe('Rosa Quispe');
    expect(auth.token()).not.toBeNull();
  });

  it('cerrar sesión (RF-03) llama a /logout y borra la sesión local, también si el servidor no contesta', () => {
    localStorage.setItem('boticasys_token', token(Date.now() / 1000 + 3600));
    const auth = crear();
    let termino = false;
    auth.cerrarSesion().subscribe(() => (termino = true));
    http.expectOne(`${URL}/logout`).error(new ProgressEvent('error'), { status: 0 });
    expect(termino).toBe(true);
    expect(auth.token()).toBeNull();
    expect(localStorage.getItem('boticasys_token')).toBeNull();
    expect(auth.estaAutenticado()).toBe(false);
  });
});
