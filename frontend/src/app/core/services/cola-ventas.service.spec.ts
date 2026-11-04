import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { ColaVentasService } from './cola-ventas.service';
import { AuthService } from './auth.service';
import { CatalogoOfflineService } from './catalogo-offline.service';
import { ConexionService } from './conexion.service';
import { AlmacenMemoria, AlmacenOffline } from '../offline/almacen-offline';
import { EntradaCola } from '../models/offline.model';
import { Usuario } from '../models/usuario.model';
import { environment } from '../../../environments/environment';

const URL = `${environment.apiUrl}/ventas`;

function usuario(id: number, nombre: string): Usuario {
  return { id, nombre, usuario: `u${id}`, rol: 'TECNICO', turno: 'Tarde', boticaNombre: 'B', boticaDireccion: null };
}

function entrada(clave: string, usuarioId: number, orden: number, nombre = 'Rosa Quispe'): EntradaCola {
  return {
    claveIdempotencia: clave,
    orden,
    usuarioId,
    usuarioNombre: nombre,
    boticaId: 1,
    cajaId: 10 + usuarioId,
    horaDispositivo: '2026-11-03T14:05:00-05:00',
    sinConexion: true,
    estado: 'PENDIENTE',
    ultimoError: null,
    cuerpo: { claveIdempotencia: clave, items: [{ productoId: 1, presentacionId: 2, cantidad: 1, origenCaptura: 'BUSQUEDA', precioCobrado: 3 }], metodoPago: 'efectivo' },
    productos: ['Paracetamol × 1 Unidad'],
    unidadesPorProducto: { 1: 1 },
  };
}

/** Tarea 22 (§3, §4, §9): dueño de la cola, orden y que nada se pierde. */
describe('ColaVentasService', () => {
  let almacen: AlmacenMemoria;
  let http: HttpTestingController;
  const usuarioActual = signal<Usuario | null>(null);
  let claims: { usuarioId: number; boticaId: number; exp: number } | null = null;

  async function crear(entradas: EntradaCola[]): Promise<ColaVentasService> {
    for (const e of entradas) await almacen.guardar('cola', e.claveIdempotencia, e);
    const cola = TestBed.inject(ColaVentasService);
    await cola.cargar();
    return cola;
  }

  function entrar(id: number, nombre: string): void {
    claims = { usuarioId: id, boticaId: 1, exp: Date.now() / 1000 + 3600 };
    usuarioActual.set(usuario(id, nombre));
  }

  /** Deja correr las promesas (IndexedDB en memoria y firstValueFrom) hasta el próximo POST. */
  async function hastaElProximoPost(): Promise<void> {
    for (let i = 0; i < 20; i++) await Promise.resolve();
  }

  beforeEach(() => {
    almacen = new AlmacenMemoria();
    usuarioActual.set(null);
    claims = null;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AlmacenOffline, useValue: almacen },
        { provide: AuthService, useValue: { usuarioActual, claims: () => claims } },
        { provide: CatalogoOfflineService, useValue: { descargar: () => of(null) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('solo envía las del usuario de la sesión, en orden, con sinConexion; las de otro esperan a su dueño', async () => {
    const cola = await crear([entrada('r2', 1, 2), entrada('m1', 2, 1, 'Miguel Soto'), entrada('r1', 1, 1)]);
    entrar(1, 'Rosa Quispe');
    expect(cola.cantidadPropias()).toBe(2);
    expect(cola.deOtrosUsuarios()).toEqual([{ nombre: 'Miguel Soto', cantidad: 1 }]);

    const vuelta = cola.enviarPendientes();
    await hastaElProximoPost();
    const primera = http.expectOne(URL);
    expect(primera.request.body.claveIdempotencia).toBe('r1');
    expect(primera.request.body.sinConexion).toEqual({ cajaId: 11, horaDispositivo: '2026-11-03T14:05:00-05:00' });
    primera.flush({ id: 1, revisiones: [] });
    await hastaElProximoPost();
    const segunda = http.expectOne(URL);
    expect(segunda.request.body.claveIdempotencia).toBe('r2');
    segunda.flush({ id: 2, revisiones: [] });
    await vuelta;

    http.verify(); // nunca se mandó la de Miguel con la sesión de Rosa
    expect(cola.cantidadPropias()).toBe(0);
    expect((await almacen.todos<EntradaCola>('cola')).map((e) => e.claveIdempotencia)).toEqual(['m1']);
  });

  it('un 401 corta la vuelta y NO borra ni marca la cola: sale sola cuando el mismo usuario vuelve a entrar', async () => {
    const cola = await crear([entrada('a', 1, 1), entrada('b', 1, 2)]);
    entrar(1, 'Rosa Quispe');

    const vuelta = cola.enviarPendientes();
    await hastaElProximoPost();
    http.expectOne(URL).flush({ error: 'NO_AUTENTICADO' }, { status: 401, statusText: 'Unauthorized' });
    await vuelta;
    http.verify(); // la segunda no se intentó

    const guardadas = await almacen.todos<EntradaCola>('cola');
    expect(guardadas).toHaveLength(2);
    expect(guardadas.every((e) => e.estado === 'PENDIENTE' && e.ultimoError === null)).toBe(true);
  });

  it('sin red corta la vuelta; un rechazo que no es de red deja la venta DETENIDA, visible, y sigue con las demás', async () => {
    const cola = await crear([entrada('a', 1, 1), entrada('b', 1, 2), entrada('c', 1, 3)]);
    entrar(1, 'Rosa Quispe');

    const vuelta = cola.enviarPendientes();
    await hastaElProximoPost();
    http.expectOne(URL).flush({ error: 'SIN_PERMISO' }, { status: 403, statusText: 'Forbidden' });
    await hastaElProximoPost();
    http.expectOne(URL).error(new ProgressEvent('error'), { status: 0 });
    await vuelta;
    http.verify(); // la tercera espera: se cortó por la red

    const a = (await almacen.obtener<EntradaCola>('cola', 'a'))!;
    expect(a.estado).toBe('DETENIDA');
    expect(a.ultimoError).toContain('otro usuario');
    expect(await almacen.todos('cola')).toHaveLength(3); // nada se borró sin un 200
    expect(TestBed.inject(ConexionService).estado()).toBe('offline');
  });

  it('una venta aceptada con revisiones queda como "enviada con observaciones"', async () => {
    const cola = await crear([entrada('a', 1, 1)]);
    entrar(1, 'Rosa Quispe');
    const vuelta = cola.enviarPendientes();
    await hastaElProximoPost();
    http.expectOne(URL).flush({ id: 7, revisiones: [{ id: 1, tipo: 'STOCK_INSUFICIENTE' }] });
    await vuelta;
    expect(cola.conObservaciones()).toHaveLength(1);
    expect(cola.conObservaciones()[0].ventaId).toBe(7);
  });

  it('un cobro en línea que quedó sin respuesta al cerrarse la app pasa a sin conexión al volver a abrirla', async () => {
    const cola = await crear([{ ...entrada('a', 1, 1), sinConexion: false }]);
    entrar(1, 'Rosa Quispe');
    expect(cola.propias()[0].sinConexion).toBe(true);
    expect(cola.cantidadDeCaja(11)).toBe(1);
  });

  it('una recarga de la app (actualización de la PWA) no pierde la cola: la instancia nueva la lee del almacén', async () => {
    const antes = await crear([]);
    entrar(1, 'Rosa Quispe');
    await antes.guardar(entrada('x', 1, 1));
    expect(antes.cantidadPropias()).toBe(1);

    // Recargar = otro inyector y otra instancia del servicio, el mismo almacén del dispositivo.
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AlmacenOffline, useValue: almacen },
        { provide: AuthService, useValue: { usuarioActual, claims: () => claims } },
        { provide: CatalogoOfflineService, useValue: { descargar: () => of(null) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const despues = TestBed.inject(ColaVentasService);
    await despues.cargar();
    expect(despues.cantidadPropias()).toBe(1);
    expect(despues.propias()[0].claveIdempotencia).toBe('x');
    await hastaElProximoPost();
    http.match(URL).forEach((r) => r.flush({ id: 1, revisiones: [] })); // el efecto de entrada la envía
  });
});
