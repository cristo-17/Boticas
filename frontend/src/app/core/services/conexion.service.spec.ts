import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ConexionService } from './conexion.service';
import { conexionInterceptor } from '../interceptors/conexion.interceptor';
import { environment } from '../../../environments/environment';

/** Tarea 22 (§2): la conexión la decide el resultado de las peticiones, no navigator.onLine. */
describe('ConexionService', () => {
  let servicio: ConexionService;
  let http: HttpTestingController;
  let cliente: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([conexionInterceptor])), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ConexionService);
    http = TestBed.inject(HttpTestingController);
    cliente = TestBed.inject(HttpClient);
  });

  afterEach(() => http.verify());

  it('una petición sin respuesta (status 0) pasa a offline; cualquier respuesta del backend vuelve a online y avisa', () => {
    let reconexiones = 0;
    servicio.reconectado$.subscribe(() => reconexiones++);

    cliente.get(`${environment.apiUrl}/productos`).subscribe({ error: () => {} });
    http.expectOne(`${environment.apiUrl}/productos?`.replace('?', '')).error(new ProgressEvent('error'), { status: 0 });
    expect(servicio.estado()).toBe('offline');

    // Un 409 de negocio también prueba que hay conexión.
    cliente.post(`${environment.apiUrl}/caja/abrir`, {}).subscribe({ error: () => {} });
    http.expectOne(`${environment.apiUrl}/caja/abrir`).flush({ error: 'CAJA_YA_ABIERTA' }, { status: 409, statusText: 'Conflict' });
    expect(servicio.estado()).toBe('online');
    expect(reconexiones).toBe(1);
  });

  it('502, 503 y 504 (el proxy sin backend detrás) cuentan como sin conexión', () => {
    for (const status of [502, 503, 504]) {
      servicio.reportarRespuesta();
      cliente.get(`${environment.apiUrl}/config`).subscribe({ error: () => {} });
      http.expectOne(`${environment.apiUrl}/config`).flush('<html>Bad gateway</html>', { status, statusText: 'x' });
      expect(servicio.estado()).toBe('offline');
    }
  });

  it('portal cautivo: 200 con HTML en la comprobación NO es conexión; JSON con version sí', async () => {
    servicio.reportarSinRed();

    const portal = servicio.comprobar();
    http.expectOne(`${environment.apiUrl}/config`).flush('<html>Inicia sesión en el wifi</html>');
    expect(await portal).toBe(false);
    expect(servicio.estado()).toBe('offline');

    const real = servicio.comprobar();
    http.expectOne(`${environment.apiUrl}/config`).flush({ igv: 0.18, version: 'local' });
    expect(await real).toBe(true);
    expect(servicio.estado()).toBe('online');
  });

  it('el evento "offline" del navegador se cree al instante; el evento "online" solo dispara una comprobación', async () => {
    window.dispatchEvent(new Event('offline'));
    expect(servicio.estado()).toBe('offline');

    window.dispatchEvent(new Event('online'));
    expect(servicio.estado()).toBe('offline'); // todavía no: falta que la API responda
    http.expectOne(`${environment.apiUrl}/config`).flush({ version: 'local' });
    await Promise.resolve();
    await Promise.resolve();
    expect(servicio.estado()).toBe('online');
  });

  it('sync = hay red y la cola se está enviando', () => {
    servicio.marcarSincronizando(true);
    expect(servicio.estado()).toBe('sync');
    servicio.reportarSinRed();
    expect(servicio.estado()).toBe('offline');
  });
});
