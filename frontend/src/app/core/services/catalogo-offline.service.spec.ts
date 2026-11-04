import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import {
  CatalogoOfflineService,
  REFRESCO_CATALOGO_MS,
  REVISION_CATALOGO_MS,
} from './catalogo-offline.service';
import { AuthService } from './auth.service';
import { ConexionService } from './conexion.service';
import { AlmacenMemoria, AlmacenOffline } from '../offline/almacen-offline';
import { CatalogoOffline } from '../models/offline.model';
import { environment } from '../../../environments/environment';

const URL = `${environment.apiUrl}/productos/catalogo-offline`;

function foto(boticaId: number, productos = 2): CatalogoOffline {
  return {
    boticaId,
    generadoEn: '2026-11-03T21:00:00-05:00',
    productos: Array.from({ length: productos }, (_, i) => ({
      id: i + 1,
      nombre: `P${i + 1}`,
      laboratorio: null,
      categoria: 'X',
      codigoBarras: null,
      presentaciones: [],
      requiereReceta: false,
      alertaVencimiento: null,
      fechaVencimientoProxima: null,
      lotesVendibles: [],
    })) as unknown as CatalogoOffline['productos'],
  };
}

/** Tarea 22 + PWA: el catálogo para vender sin conexión queda en el dispositivo desde el login. */
describe('CatalogoOfflineService', () => {
  let http: HttpTestingController;
  let almacen: AlmacenMemoria;
  let enLinea: ReturnType<typeof signal<boolean>>;
  let reconectado: Subject<void>;
  let boticaId: number;

  beforeEach(() => {
    almacen = new AlmacenMemoria();
    enLinea = signal(true);
    reconectado = new Subject();
    boticaId = 1;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AlmacenOffline, useValue: almacen },
        { provide: AuthService, useValue: { claims: () => ({ boticaId }) } },
        {
          provide: ConexionService,
          useValue: { enLinea, reconectado$: reconectado.asObservable() },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  const tick = async () => {
    for (let i = 0; i < 10; i++) await Promise.resolve();
  };

  it('descargar: actualizando mientras baja, guarda la foto en el almacén y expone cantidad y fecha', async () => {
    const s = TestBed.inject(CatalogoOfflineService);
    s.descargar().subscribe();
    expect(s.actualizando()).toBe(true);
    http.expectOne(URL).flush(foto(1, 3));
    await tick();
    expect(s.actualizando()).toBe(false);
    expect(s.error()).toBeNull();
    expect(s.cantidadProductos()).toBe(3);
    expect(s.generadoEn()).toBe('2026-11-03T21:00:00-05:00');
    expect((await almacen.obtener<CatalogoOffline>('catalogo', '1'))?.productos).toHaveLength(3);
  });

  it('descargar con la red caída: error visible, actualizando se apaga en finalize y la foto vieja sigue', async () => {
    await almacen.guardar('catalogo', '1', foto(1, 2));
    const s = TestBed.inject(CatalogoOfflineService);
    await s.cargarGuardado();
    s.descargar().subscribe({ error: () => {} });
    http.expectOne(URL).error(new ProgressEvent('error'), { status: 0 });
    expect(s.actualizando()).toBe(false);
    expect(s.error()).toContain('sin conexión');
    expect(s.cantidadProductos()).toBe(2);
  });

  it('mantenerActualizado (Shell, tras el login): lee la foto guardada y la renueva si hay conexión', async () => {
    await almacen.guardar('catalogo', '1', foto(1, 2));
    const s = TestBed.inject(CatalogoOfflineService);
    const detener = s.mantenerActualizado();
    await tick();
    expect(s.cantidadProductos()).toBe(2); // ya se puede vender sin conexión con la guardada
    http.expectOne(URL).flush(foto(1, 5));
    await tick();
    expect(s.cantidadProductos()).toBe(5);
    detener();
  });

  it('mantenerActualizado sin conexión: no pide nada; al volver la señal, la baja', async () => {
    enLinea.set(false);
    const s = TestBed.inject(CatalogoOfflineService);
    const detener = s.mantenerActualizado();
    await tick();
    http.expectNone(URL);
    enLinea.set(true);
    reconectado.next();
    http.expectOne(URL).flush(foto(1));
    detener();
  });

  it('con conexión, la renueva cada 15 minutos (no antes) y deja de hacerlo al detenerse', async () => {
    vi.useFakeTimers();
    const s = TestBed.inject(CatalogoOfflineService);
    const detener = s.mantenerActualizado();
    await vi.advanceTimersByTimeAsync(0);
    http.expectOne(URL).flush(foto(1));
    await vi.advanceTimersByTimeAsync(0);

    await vi.advanceTimersByTimeAsync(REFRESCO_CATALOGO_MS - REVISION_CATALOGO_MS);
    http.expectNone(URL);
    await vi.advanceTimersByTimeAsync(REVISION_CATALOGO_MS);
    http.expectOne(URL).flush(foto(1));
    await vi.advanceTimersByTimeAsync(0);

    detener();
    await vi.advanceTimersByTimeAsync(REFRESCO_CATALOGO_MS * 2);
    http.expectNone(URL);
  });

  it('otra botica en el mismo equipo no usa la foto en memoria de la sesión anterior', async () => {
    const s = TestBed.inject(CatalogoOfflineService);
    s.descargar().subscribe();
    http.expectOne(URL).flush(foto(1, 4));
    await tick();
    boticaId = 2;
    await s.cargarGuardado();
    expect(s.catalogo()).toBeNull();
  });
});
