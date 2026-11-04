import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { vi } from 'vitest';
import { TIEMPO_ESPERA_API_MS, conexionInterceptor } from './conexion.interceptor';
import { ErrorTraducido, MENSAJE_SIN_CONEXION } from './error.interceptor';
import { ConexionService } from '../services/conexion.service';
import { esFalloDeRed } from '../utils/red.util';
import { environment } from '../../../environments/environment';

const API = environment.apiUrl;
type ErrorConTraduccion = HttpErrorResponse & { traducido?: ErrorTraducido };

/**
 * offline-fix (punto 3): sin conexión, una petición que no es la venta falla al
 * instante con "Esta acción necesita conexión" ("Abrir caja" se quedaba cargando
 * sin fin en el celular). Con conexión, el servidor tiene TIEMPO_ESPERA_API_MS.
 */
describe('conexionInterceptor — sin conexión', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let enLinea: ReturnType<typeof signal<boolean>>;
  let comprobaciones: number;
  let sinRed: number;

  beforeEach(() => {
    enLinea = signal(true);
    comprobaciones = 0;
    sinRed = 0;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([conexionInterceptor])),
        provideHttpClientTesting(),
        {
          provide: ConexionService,
          useValue: {
            enLinea,
            comprobar: async () => (comprobaciones++, false),
            reportarSinRed: () => void sinRed++,
            reportarRespuesta: () => {},
          },
        },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  function fallo(peticion: ReturnType<HttpClient['get']>): Promise<ErrorConTraduccion> {
    return new Promise((ok, mal) =>
      peticion.subscribe({ next: () => mal(new Error('no debía responder')), error: ok }),
    );
  }

  it('sin conexión, "Abrir caja" (y cualquier otra) falla AL INSTANTE, sin salir, con "Esta acción necesita conexión"', async () => {
    enLinea.set(false);
    const err = await fallo(http.post(`${API}/caja/abrir`, { montoInicial: 50, turno: 'Tarde' }));
    backend.expectNone(`${API}/caja/abrir`);
    expect(err.status).toBe(0);
    expect(esFalloDeRed(err)).toBe(true); // los servicios con respaldo local (búsqueda, catálogo) siguen igual
    expect(err.traducido?.mensaje).toBe(MENSAJE_SIN_CONEXION);
    expect(err.traducido?.codigo).toBe('SIN_CONEXION');
    expect(comprobaciones).toBe(1); // por si la señal ya volvió
  });

  it('sin conexión, la venta SÍ sale: la decide VentaService (cola, idempotencia, su propio tiempo de espera)', () => {
    enLinea.set(false);
    http.post(`${API}/ventas`, {}).subscribe({ error: () => {} });
    backend.expectOne(`${API}/ventas`).flush({ id: 1 });
  });

  it('con conexión pero el servidor no contesta en 60 s: el mismo error, y la conexión pasa a "sin red"', async () => {
    vi.useFakeTimers();
    const resultado = fallo(http.get(`${API}/caja/hoy`));
    const req = backend.expectOne(`${API}/caja/hoy`);
    vi.advanceTimersByTime(TIEMPO_ESPERA_API_MS - 1);
    expect(req.cancelled).toBe(false);
    vi.advanceTimersByTime(1);
    const err = await resultado;
    expect(req.cancelled).toBe(true);
    expect(err.traducido?.mensaje).toBe(MENSAJE_SIN_CONEXION);
    expect(sinRed).toBe(1);
  });

  it('con conexión, una respuesta normal pasa igual', () => {
    let cuerpo: unknown;
    http.get(`${API}/caja/hoy`).subscribe((c) => (cuerpo = c));
    backend.expectOne(`${API}/caja/hoy`).flush({ id: 3 });
    expect(cuerpo).toEqual({ id: 3 });
  });

  it('lo que no es de la API no se toca', () => {
    enLinea.set(false);
    http.get('/assets/x.json').subscribe({ error: () => {} });
    backend.expectOne('/assets/x.json').flush({});
  });
});
