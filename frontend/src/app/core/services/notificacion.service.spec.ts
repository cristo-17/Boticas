import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Mock, vi } from 'vitest';
import { NotificacionService } from './notificacion.service';
import { AuthService } from './auth.service';
import { Usuario } from '../models/usuario.model';
import { Notificacion } from '../models/notificacion.model';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/notificaciones`;

/** Sustituto de EventSource: registra cada stream abierto y deja simular eventos y caídas. */
class EventSourceFalso {
  static instancias: EventSourceFalso[] = [];
  cerrado = false;
  onerror: (() => void) | null = null;
  private readonly oyentes = new Map<string, ((e: MessageEvent) => void)[]>();

  constructor(readonly url: string) {
    EventSourceFalso.instancias.push(this);
  }

  addEventListener(tipo: string, fn: (e: MessageEvent) => void): void {
    this.oyentes.set(tipo, [...(this.oyentes.get(tipo) ?? []), fn]);
  }

  close(): void {
    this.cerrado = true;
  }

  emitir(tipo: string, data = ''): void {
    (this.oyentes.get(tipo) ?? []).forEach((fn) => fn(new MessageEvent(tipo, { data })));
  }

  caer(): void {
    this.onerror?.();
  }

  static abiertos(): EventSourceFalso[] {
    return EventSourceFalso.instancias.filter((es) => !es.cerrado);
  }
}

function usuario(id: number, rol: Usuario['rol']): Usuario {
  return { id, nombre: `U${id}`, usuario: `u${id}`, rol, turno: 'Tarde', boticaNombre: 'B', boticaDireccion: 'D' };
}

function notificacion(id: number): Notificacion {
  return { id, tipo: 'STOCK_CRITICO', titulo: `N${id}`, mensaje: 'm', leido: false, fechaCreacion: '2026-10-14T10:00:00-05:00' };
}

describe('NotificacionService — sesión y stream (H1)', () => {
  let service: NotificacionService;
  let http: HttpTestingController;
  const usuarioActual = signal<Usuario | null>(null);

  /** Responde el POST /ticket pendiente y el GET del historial que dispara el cambio de usuario. */
  function entregarTicket(ticket: string, historial: Notificacion[] = []): void {
    http.expectOne({ method: 'POST', url: `${BASE_URL}/ticket` }).flush({ ticket, expiraEnSegundos: 60 });
    http.match({ method: 'GET', url: BASE_URL }).forEach((r) => r.flush(historial));
  }

  beforeEach(() => {
    EventSourceFalso.instancias = [];
    vi.stubGlobal('EventSource', EventSourceFalso);
    usuarioActual.set(null);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { usuarioActual: usuarioActual.asReadonly() } },
      ],
    });
    service = TestBed.inject(NotificacionService);
    http = TestBed.inject(HttpTestingController);
    TestBed.tick();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    http.verify();
  });

  it('sin usuario no pide ticket ni abre stream', () => {
    http.expectNone(`${BASE_URL}/ticket`);
    expect(EventSourceFalso.instancias).toHaveLength(0);
  });

  it('al entrar un usuario abre UN stream con ticket, nunca con el JWT en la URL', () => {
    usuarioActual.set(usuario(1, 'TECNICO'));
    TestBed.tick();
    entregarTicket('T-1');

    expect(EventSourceFalso.abiertos()).toHaveLength(1);
    expect(EventSourceFalso.abiertos()[0].url).toBe(`${BASE_URL}/stream?ticket=T-1`);
    expect(EventSourceFalso.abiertos()[0].url).not.toContain('token');
  });

  it('al salir (usuario null) cierra el stream y vacía la lista', () => {
    usuarioActual.set(usuario(1, 'TECNICO'));
    TestBed.tick();
    entregarTicket('T-1', [notificacion(1)]);
    expect(service.notificaciones()).toHaveLength(1);

    usuarioActual.set(null);
    TestBed.tick();

    expect(EventSourceFalso.abiertos()).toHaveLength(0);
    expect(service.notificaciones()).toEqual([]);
    expect(service.conectadoSse()).toBe(false);
  });

  it('técnico -> salir -> administrador: queda un solo stream y es el del usuario nuevo', () => {
    usuarioActual.set(usuario(1, 'TECNICO'));
    TestBed.tick();
    entregarTicket('T-TECNICO', [notificacion(1)]);

    usuarioActual.set(null);
    TestBed.tick();
    usuarioActual.set(usuario(2, 'ADMINISTRADOR'));
    TestBed.tick();
    entregarTicket('T-ADMIN', [notificacion(2)]);

    expect(EventSourceFalso.abiertos()).toHaveLength(1);
    expect(EventSourceFalso.abiertos()[0].url).toContain('ticket=T-ADMIN');
    expect(service.notificaciones().map((n) => n.id)).toEqual([2]);
  });

  it('cambio directo de usuario con el ticket viejo todavía en vuelo: no abre el stream viejo', () => {
    usuarioActual.set(usuario(1, 'TECNICO'));
    TestBed.tick();
    const ticketViejo = http.expectOne({ method: 'POST', url: `${BASE_URL}/ticket` });
    http.match({ method: 'GET', url: BASE_URL }).forEach((r) => r.flush([]));

    usuarioActual.set(usuario(2, 'ADMINISTRADOR'));
    TestBed.tick();
    expect(ticketViejo.cancelled).toBe(true);
    entregarTicket('T-ADMIN');

    expect(EventSourceFalso.instancias.map((es) => es.url)).toEqual([`${BASE_URL}/stream?ticket=T-ADMIN`]);
  });

  it('si el stream se cae, lo cierra y reabre con un ticket NUEVO (el viejo ya se canjeó)', () => {
    vi.useFakeTimers();
    usuarioActual.set(usuario(1, 'TECNICO'));
    TestBed.tick();
    entregarTicket('T-1');
    const primero = EventSourceFalso.abiertos()[0];

    primero.caer();
    expect(primero.cerrado).toBe(true);
    vi.advanceTimersByTime(5000);
    entregarTicket('T-2');

    expect(EventSourceFalso.abiertos()).toHaveLength(1);
    expect(EventSourceFalso.abiertos()[0].url).toContain('ticket=T-2');
  });

  it('una alerta que llega por el stream entra primera en la lista', () => {
    usuarioActual.set(usuario(1, 'TECNICO'));
    TestBed.tick();
    entregarTicket('T-1', [notificacion(1)]);

    EventSourceFalso.abiertos()[0].emitir('ALERTA', JSON.stringify(notificacion(9)));

    expect(service.notificaciones().map((n) => n.id)).toEqual([9, 1]);
  });
});

describe('NotificacionService — notificación nativa (H3)', () => {
  const usuarioActual = signal<Usuario | null>(null);
  let constructorNotification: Mock<(titulo: string, opciones?: NotificationOptions) => void>;
  let showNotification: Mock<(titulo: string, opciones?: NotificationOptions) => Promise<void>>;

  /** Notification falso: Android lanza "Illegal constructor" con new Notification(); acá solo contamos llamadas. */
  function stubNotification(permiso: NotificationPermission): void {
    constructorNotification = vi.fn<(titulo: string, opciones?: NotificationOptions) => void>();
    const Falso = function (this: unknown, titulo: string, opciones: NotificationOptions) {
      constructorNotification(titulo, opciones);
    } as unknown as { permission: NotificationPermission };
    Falso.permission = permiso;
    vi.stubGlobal('Notification', Falso);
  }

  function stubServiceWorker(conRegistro: boolean): void {
    showNotification = vi.fn<(titulo: string, opciones?: NotificationOptions) => Promise<void>>().mockResolvedValue(undefined);
    const registro = { showNotification };
    Object.defineProperty(navigator, 'serviceWorker', {
      configurable: true,
      value: {
        getRegistration: vi.fn().mockResolvedValue(conRegistro ? registro : undefined),
        ready: conRegistro ? Promise.resolve(registro) : new Promise(() => {}),
      },
    });
  }

  /** Arranca el servicio con sesión abierta y devuelve el stream ya conectado. */
  function conStreamAbierto(): EventSourceFalso {
    EventSourceFalso.instancias = [];
    vi.stubGlobal('EventSource', EventSourceFalso);
    usuarioActual.set(null);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { usuarioActual: usuarioActual.asReadonly() } },
      ],
    });
    TestBed.inject(NotificacionService);
    const http = TestBed.inject(HttpTestingController);
    usuarioActual.set(usuario(1, 'TECNICO'));
    TestBed.tick();
    http.expectOne({ method: 'POST', url: `${BASE_URL}/ticket` }).flush({ ticket: 'T', expiraEnSegundos: 60 });
    http.match({ method: 'GET', url: BASE_URL }).forEach((r) => r.flush([]));
    return EventSourceFalso.abiertos()[0];
  }

  afterEach(() => {
    vi.unstubAllGlobals();
    delete (navigator as unknown as Record<string, unknown>)['serviceWorker'];
  });

  it('con service worker registrado usa registration.showNotification (lo único que funciona en Android)', async () => {
    stubNotification('granted');
    stubServiceWorker(true);
    const stream = conStreamAbierto();

    stream.emitir('ALERTA', JSON.stringify(notificacion(7)));
    await vi.waitFor(() => expect(showNotification).toHaveBeenCalled());

    expect(showNotification).toHaveBeenCalledWith('N7', expect.objectContaining({ body: 'm' }));
    expect(constructorNotification).not.toHaveBeenCalled();
  });

  it('sin service worker (ng serve) cae a new Notification', async () => {
    stubNotification('granted');
    stubServiceWorker(false);
    const stream = conStreamAbierto();

    stream.emitir('ALERTA', JSON.stringify(notificacion(7)));
    await vi.waitFor(() => expect(constructorNotification).toHaveBeenCalled());

    expect(constructorNotification).toHaveBeenCalledWith('N7', expect.objectContaining({ body: 'm' }));
  });

  it('sin permiso concedido no muestra nada', async () => {
    stubNotification('default');
    stubServiceWorker(true);
    const stream = conStreamAbierto();

    stream.emitir('ALERTA', JSON.stringify(notificacion(7)));
    await new Promise((r) => setTimeout(r, 0));

    expect(showNotification).not.toHaveBeenCalled();
    expect(constructorNotification).not.toHaveBeenCalled();
  });
});

describe('NotificacionService — molde de servicio (H4)', () => {
  let service: NotificacionService;
  let http: HttpTestingController;
  const usuarioActual = signal<Usuario | null>(null);

  /** Sesión abierta con historial [1, 2] (ambas no leídas); el stream no importa acá. */
  function conHistorial(): void {
    http.expectOne({ method: 'POST', url: `${BASE_URL}/ticket` }).flush({ ticket: 'T', expiraEnSegundos: 60 });
    http.expectOne({ method: 'GET', url: BASE_URL }).flush([notificacion(1), notificacion(2)]);
  }

  beforeEach(() => {
    EventSourceFalso.instancias = [];
    vi.stubGlobal('EventSource', EventSourceFalso);
    usuarioActual.set(null);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { usuarioActual: usuarioActual.asReadonly() } },
      ],
    });
    service = TestBed.inject(NotificacionService);
    http = TestBed.inject(HttpTestingController);
    usuarioActual.set(usuario(1, 'TECNICO'));
    TestBed.tick();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    http.verify();
  });

  it('historial: cargando mientras viaja el GET y se apaga al responder', () => {
    http.expectOne({ method: 'POST', url: `${BASE_URL}/ticket` }).flush({ ticket: 'T', expiraEnSegundos: 60 });
    const get = http.expectOne({ method: 'GET', url: BASE_URL });
    expect(service.cargando()).toBe(true);

    get.flush([notificacion(1)]);

    expect(service.cargando()).toBe(false);
    expect(service.error()).toBeNull();
  });

  it('historial que falla: muestra el error, apaga cargando y NO tumba el stream', () => {
    http.expectOne({ method: 'POST', url: `${BASE_URL}/ticket` }).flush({ ticket: 'T', expiraEnSegundos: 60 });
    http.expectOne({ method: 'GET', url: BASE_URL }).flush(null, { status: 500, statusText: 'Error' });

    expect(service.error()).not.toBeNull();
    expect(service.cargando()).toBe(false);
    expect(EventSourceFalso.abiertos()).toHaveLength(1);
  });

  it('marcarLeida devuelve un Observable y marca al instante (optimista)', () => {
    conHistorial();

    service.marcarLeida(1).subscribe();

    expect(service.notificaciones().find((n) => n.id === 1)?.leido).toBe(true);
    http.expectOne({ method: 'PUT', url: `${BASE_URL}/1/leer` }).flush(null);
    expect(service.notificaciones().find((n) => n.id === 1)?.leido).toBe(true);
    expect(service.error()).toBeNull();
  });

  it('si el PUT de leída falla, vuelve a no leída y se muestra el error', () => {
    conHistorial();

    service.marcarLeida(1).subscribe({ error: () => {} });
    http.expectOne({ method: 'PUT', url: `${BASE_URL}/1/leer` }).flush(null, { status: 404, statusText: 'Not Found' });

    expect(service.notificaciones().find((n) => n.id === 1)?.leido).toBe(false);
    expect(service.noLeidasCount()).toBe(2);
    expect(service.error()).not.toBeNull();
  });

  it('si falla "marcar todas", vuelven a no leídas solo las que lo eran', () => {
    http.expectOne({ method: 'POST', url: `${BASE_URL}/ticket` }).flush({ ticket: 'T', expiraEnSegundos: 60 });
    http.expectOne({ method: 'GET', url: BASE_URL }).flush([notificacion(1), { ...notificacion(2), leido: true }]);

    service.marcarTodasLeidas().subscribe({ error: () => {} });
    expect(service.noLeidasCount()).toBe(0);
    http.expectOne({ method: 'PUT', url: `${BASE_URL}/leer-todas` }).flush(null, { status: 500, statusText: 'Error' });

    expect(service.notificaciones().map((n) => [n.id, n.leido])).toEqual([[1, false], [2, true]]);
    expect(service.error()).not.toBeNull();
  });
});
