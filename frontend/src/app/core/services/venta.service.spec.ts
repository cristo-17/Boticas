import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import {
  MENSAJE_SESION_SIN_CONEXION,
  MENSAJE_SIN_CAJA_SIN_CONEXION,
  ResultadoCobro,
  VentaService,
} from './venta.service';
import { AuthService } from './auth.service';
import { CajaService } from './caja.service';
import { ColaVentasService } from './cola-ventas.service';
import { ConexionService } from './conexion.service';
import { AlertaService } from './alerta.service';
import { EntradaCola } from '../models/offline.model';
import { environment } from '../../../environments/environment';

const URL = `${environment.apiUrl}/ventas`;
const PEDIDO = {
  items: [
    {
      productoId: 1,
      presentacionId: 2,
      cantidad: 1,
      origenCaptura: 'BUSQUEDA' as const,
      precioCobrado: 3,
    },
  ],
  metodoPago: 'efectivo' as const,
  receta: null,
};

/** offline-fix (punto 2): sin caja abierta conocida, cobrar sin conexión no recarga ni se pierde: dice qué hacer. */
describe('VentaService — cobrar sin conexión', () => {
  let http: HttpTestingController;
  let enLinea: ReturnType<typeof signal<boolean>>;
  let caja: { id: number; abierta: boolean } | null;
  let sesionVigente: boolean;
  let guardadas: EntradaCola[];

  beforeEach(() => {
    enLinea = signal(true);
    caja = null;
    sesionVigente = true;
    guardadas = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            claims: () => ({ usuarioId: 1, boticaId: 1, exp: Date.now() / 1000 + 3600 }),
            usuarioActual: () => ({ id: 1, nombre: 'Luis' }),
            sesionVigente: () => sesionVigente,
          },
        },
        { provide: CajaService, useValue: { cajaAbiertaConocida: () => caja } },
        {
          provide: ColaVentasService,
          useValue: {
            siguienteOrden: async () => 1,
            guardar: async (e: EntradaCola) => void guardadas.push(e),
            quitar: async () => {},
            marcarSinConexion: async () => {},
          },
        },
        {
          provide: ConexionService,
          useValue: { enLinea, reportarSinRed: () => enLinea.set(false) },
        },
        { provide: AlertaService, useValue: { obtenerResumen: () => of(null) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function cobrar(): Promise<{ resultado?: ResultadoCobro; error?: unknown }> {
    return new Promise((ok) =>
      TestBed.inject(VentaService)
        .registrarVenta(PEDIDO)
        .subscribe({ next: (resultado) => ok({ resultado }), error: (error) => ok({ error }) }),
    );
  }

  it('sin caja abierta conocida y sin conexión: no manda nada, no la encola y pide abrir la caja con conexión', async () => {
    enLinea.set(false);
    const servicio = TestBed.inject(VentaService);
    const { resultado, error } = await cobrar();

    expect(resultado).toBeUndefined();
    expect(error).toBeTruthy();
    http.expectNone(URL);
    expect(guardadas).toHaveLength(0);
    expect(servicio.error()).toBe(MENSAJE_SIN_CAJA_SIN_CONEXION);
    expect(servicio.cargando()).toBe(false);
  });

  it('sin caja abierta conocida, la app creía tener conexión y la red falla: el mismo mensaje, no "sin conexión con el servidor"', async () => {
    const servicio = TestBed.inject(VentaService);
    const pendiente = cobrar();
    await Promise.resolve();
    await Promise.resolve();
    http.expectOne(URL).error(new ProgressEvent('error'), { status: 0 });
    const { error } = await pendiente;

    expect(error).toBeTruthy();
    expect(servicio.error()).toBe(MENSAJE_SIN_CAJA_SIN_CONEXION);
  });

  it('con caja conocida pero la sesión vencida: lo dice (entrar de nuevo con conexión)', async () => {
    enLinea.set(false);
    caja = { id: 10, abierta: true };
    sesionVigente = false;
    const servicio = TestBed.inject(VentaService);
    await cobrar();
    http.expectNone(URL);
    expect(servicio.error()).toBe(MENSAJE_SESION_SIN_CONEXION);
  });

  it('con caja conocida y sin conexión: sigue quedando en la cola, como en la Tarea 22', async () => {
    enLinea.set(false);
    caja = { id: 10, abierta: true };
    const { resultado } = await cobrar();
    http.expectNone(URL);
    expect(resultado?.tipo).toBe('pendiente');
    expect(guardadas).toHaveLength(1);
    expect(guardadas[0].cajaId).toBe(10);
  });
});
