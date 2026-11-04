import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { SwUpdate, UnrecoverableStateEvent, VersionEvent } from '@angular/service-worker';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import {
  ActualizacionAppService,
  INTERVALO_BUSQUEDA_VERSION_MS,
  RECARGAR_PAGINA,
} from './actualizacion-app.service';
import { ColaVentasService } from './cola-ventas.service';
import { VentaEnCursoService } from './venta-en-curso.service';
import { ConexionService } from './conexion.service';

const LISTA: VersionEvent = {
  type: 'VERSION_READY',
  currentVersion: { hash: 'vieja' },
  latestVersion: { hash: 'nueva' },
};

/** PWA: el celular toma la versión nueva sin reinicios, y nunca en medio de una venta. */
describe('ActualizacionAppService', () => {
  let versiones: Subject<VersionEvent>;
  let irrecuperable: Subject<UnrecoverableStateEvent>;
  let busquedas: number;
  let recargas: number;
  let enviando: ReturnType<typeof signal<boolean>>;
  let habilitado: boolean;
  let enLinea: ReturnType<typeof signal<boolean>>;

  beforeEach(() => {
    vi.useFakeTimers();
    versiones = new Subject();
    irrecuperable = new Subject();
    busquedas = 0;
    recargas = 0;
    enviando = signal(false);
    habilitado = true;
    enLinea = signal(true);
    TestBed.configureTestingModule({
      providers: [
        {
          provide: SwUpdate,
          useValue: {
            get isEnabled() {
              return habilitado;
            },
            versionUpdates: versiones.asObservable(),
            unrecoverable: irrecuperable.asObservable(),
            checkForUpdate: () => {
              busquedas++;
              return Promise.resolve(false);
            },
          },
        },
        { provide: ColaVentasService, useValue: { enviando } },
        { provide: ConexionService, useValue: { enLinea } },
        { provide: RECARGAR_PAGINA, useValue: () => void recargas++ },
      ],
    });
  });

  afterEach(() => vi.useRealTimers());

  function iniciar(): ActualizacionAppService {
    const servicio = TestBed.inject(ActualizacionAppService);
    servicio.iniciar();
    return servicio;
  }

  it('busca una versión nueva al arrancar, al volver la pestaña a visible y cada 15 minutos', () => {
    iniciar();
    expect(busquedas).toBe(1);

    document.dispatchEvent(new Event('visibilitychange')); // jsdom: visibilityState = 'visible'
    expect(busquedas).toBe(2);

    vi.advanceTimersByTime(INTERVALO_BUSQUEDA_VERSION_MS);
    expect(busquedas).toBe(3);
    vi.advanceTimersByTime(INTERVALO_BUSQUEDA_VERSION_MS);
    expect(busquedas).toBe(4);
  });

  it('iniciar dos veces no duplica las búsquedas', () => {
    const servicio = iniciar();
    servicio.iniciar();
    vi.advanceTimersByTime(INTERVALO_BUSQUEDA_VERSION_MS);
    expect(busquedas).toBe(2);
  });

  it('con la versión lista, sin venta en curso y la cola quieta: recarga sola, sin aviso', () => {
    const servicio = iniciar();
    versiones.next(LISTA);
    expect(recargas).toBe(1);
    expect(servicio.hayVersionNueva()).toBe(false);
  });

  it('con una venta en curso NO recarga: muestra el aviso y recarga recién al tocar "Actualizar"', () => {
    const servicio = iniciar();
    TestBed.inject(VentaEnCursoService).marcar(true);
    versiones.next(LISTA);
    expect(recargas).toBe(0);
    expect(servicio.hayVersionNueva()).toBe(true);

    servicio.actualizarAhora();
    expect(recargas).toBe(1);
  });

  it('con la cola enviando NO recarga: muestra el aviso', () => {
    const servicio = iniciar();
    enviando.set(true);
    versiones.next(LISTA);
    expect(recargas).toBe(0);
    expect(servicio.hayVersionNueva()).toBe(true);
  });

  it('los otros eventos de versión (detectada, sin cambios, falló la instalación) no hacen nada', () => {
    const servicio = iniciar();
    versiones.next({ type: 'VERSION_DETECTED', version: { hash: 'nueva' } });
    versiones.next({ type: 'NO_NEW_VERSION_DETECTED', version: { hash: 'vieja' } });
    versiones.next({ type: 'VERSION_INSTALLATION_FAILED', version: { hash: 'nueva' }, error: 'x' });
    expect(recargas).toBe(0);
    expect(servicio.hayVersionNueva()).toBe(false);
  });

  it('sin conexión NUNCA recarga sola, aunque el carrito esté vacío: muestra el aviso ([FE-016])', () => {
    const servicio = iniciar();
    enLinea.set(false);
    versiones.next(LISTA);
    expect(recargas).toBe(0);
    expect(servicio.hayVersionNueva()).toBe(true);
  });

  it('estado irrecuperable con una venta en curso o sin conexión: aviso, no recarga en medio de la venta', () => {
    const servicio = iniciar();
    TestBed.inject(VentaEnCursoService).marcar(true);
    irrecuperable.next({ type: 'UNRECOVERABLE_STATE', reason: 'x' });
    expect(recargas).toBe(0);
    expect(servicio.hayVersionNueva()).toBe(true);
  });

  it('estado irrecuperable del service worker sin venta en curso y con conexión: recarga', () => {
    iniciar();
    irrecuperable.next({ type: 'UNRECOVERABLE_STATE', reason: 'faltan archivos de esta versión' });
    expect(recargas).toBe(1);
  });

  it('sin red, checkForUpdate rechaza y no rompe nada', async () => {
    TestBed.overrideProvider(SwUpdate, {
      useValue: {
        isEnabled: true,
        versionUpdates: versiones.asObservable(),
        unrecoverable: irrecuperable.asObservable(),
        checkForUpdate: () => Promise.reject(new Error('sin red')),
      },
    });
    expect(() => iniciar()).not.toThrow();
    await Promise.resolve();
  });

  it('sin service worker (desarrollo, tests) no hace nada', () => {
    habilitado = false;
    iniciar();
    vi.advanceTimersByTime(INTERVALO_BUSQUEDA_VERSION_MS);
    expect(busquedas).toBe(0);
  });
});
