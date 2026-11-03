import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { Usuario } from '../../core/models/usuario.model';
import { fechaLimaIso, sumarDias } from '../../core/utils/fecha-lima.util';
import { AlertasScreen } from './alertas';
import { environment } from '../../../environments/environment';

const API_URL = environment.apiUrl;

describe('AlertasScreen — ventasHoy por rol (Tarea 18)', () => {
  let fixture: ComponentFixture<AlertasScreen>;
  let http: HttpTestingController;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AlertasScreen],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AlertasScreen);
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  it('para TECNICO (ventasHoy === null) muestra Boletas del día y oculta el monto de ventas', () => {
    fixture.detectChanges();
    http.expectOne(`${API_URL}/dashboard/resumen`).flush({
      ventasHoy: null,
      ventasHoyVariacionPct: null,
      boletasHoy: 14,
      productosPorVencer: 2,
      productosPorVencerCriticos: 1,
      stockCritico: 3,
      stockAgotado: 0,
      cajaEstado: 'ABIERTA',
      cajaHoraApertura: '2026-10-14T08:00:00-05:00',
    });
    http
      .expectOne((r) => r.url === `${API_URL}/alertas`)
      .flush({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 });
    fixture.detectChanges();

    const texto = el().textContent ?? '';
    expect(texto).toContain('Boletas del día');
    expect(texto).toContain('14');
    expect(texto).not.toContain('Ventas del día');
  });

  it('para ADMINISTRADOR (ventasHoy numérico) muestra Ventas del día con monto y variación', () => {
    fixture.detectChanges();
    http.expectOne(`${API_URL}/dashboard/resumen`).flush({
      ventasHoy: 420.5,
      ventasHoyVariacionPct: 12.5,
      boletasHoy: 14,
      productosPorVencer: 2,
      productosPorVencerCriticos: 1,
      stockCritico: 3,
      stockAgotado: 0,
      cajaEstado: 'ABIERTA',
      cajaHoraApertura: '2026-10-14T08:00:00-05:00',
    });
    http
      .expectOne((r) => r.url === `${API_URL}/alertas`)
      .flush({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 });
    fixture.detectChanges();

    const texto = el().textContent ?? '';
    expect(texto).toContain('Ventas del día');
    expect(texto).toContain('S/ 420.50');
    expect(texto).toContain('+12.5% vs. ayer');
  });
});

describe('AlertasScreen — gráfico de 7 días por rol (Tarea 18, Bloque C)', () => {
  const RESUMEN_BASE = {
    boletasHoy: 3,
    productosPorVencer: 0,
    productosPorVencerCriticos: 0,
    stockCritico: 0,
    stockAgotado: 0,
    cajaEstado: 'CERRADA',
    cajaHoraApertura: null,
  };

  function montar(rol: 'ADMINISTRADOR' | 'TECNICO') {
    const usuario = signal({ id: 1, nombre: 'Prueba Rol', usuario: 'p', rol, boticaId: 1 } as unknown as Usuario);
    TestBed.configureTestingModule({
      imports: [AlertasScreen],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { usuarioActual: usuario.asReadonly() } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AlertasScreen);
    fixture.detectChanges();
    return { http, fixture };
  }

  /** Con sesión iniciada arranca el canal de notificaciones (ajeno a este caso): se descarta. */
  function verificar(http: HttpTestingController) {
    http.match((r) => r.url.startsWith(`${API_URL}/notificaciones`));
    http.verify();
  }

  function flushDashboard(http: HttpTestingController, ventasHoy: number | null) {
    http.expectOne(`${API_URL}/dashboard/resumen`).flush({
      ...RESUMEN_BASE,
      ventasHoy,
      ventasHoyVariacionPct: ventasHoy === null ? null : 0,
    });
    http
      .expectOne((r) => r.url === `${API_URL}/alertas`)
      .flush({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 });
  }

  it('ADMINISTRADOR: pide los últimos 7 días de Lima (agrupar=dia) y muestra el gráfico que lleva a Resultados', () => {
    const { http, fixture } = montar('ADMINISTRADOR');
    flushDashboard(http, 120);
    const req = http.expectOne((r) => r.url === `${API_URL}/reportes/resultados`);
    const hoy = fechaLimaIso();
    expect(req.request.params.get('hasta')).toBe(hoy);
    expect(req.request.params.get('desde')).toBe(sumarDias(hoy, -6));
    expect(req.request.params.get('agrupar')).toBe('dia');
    req.flush({
      desde: sumarDias(hoy, -6),
      hasta: hoy,
      agrupar: 'dia',
      nota: '',
      filas: [{ periodo: hoy, etiqueta: 'hoy', ventas: 10, costoVentas: 4, margenBruto: 6, mermasCosto: 0, egresos: 0, gastos: 4, resultado: 6 }],
      totales: { ventas: 10, costoVentas: 4, margenBruto: 6, mermasCosto: 0, egresos: 0, gastos: 4, resultado: 6 },
    });
    fixture.detectChanges();

    const tarjeta = fixture.nativeElement.querySelector('[data-testid="tendencia-7-dias"]') as HTMLButtonElement;
    expect(tarjeta).not.toBeNull();
    expect(tarjeta.querySelector('app-grafico-resultados')).not.toBeNull();
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    tarjeta.click();
    expect(navegar).toHaveBeenCalledWith(['/reportes'], { queryParams: undefined });
    verificar(http);
    fixture.destroy();
  });

  it('ADMINISTRADOR: si falla el reporte, la tarjeta muestra el error (regla 1)', () => {
    const { http, fixture } = montar('ADMINISTRADOR');
    flushDashboard(http, 120);
    http
      .expectOne((r) => r.url === `${API_URL}/reportes/resultados`)
      .flush({ error: 'ERROR_INTERNO' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    const tarjeta = fixture.nativeElement.querySelector('[data-testid="tendencia-7-dias"]') as HTMLElement;
    expect(tarjeta.querySelector('[role="alert"]')?.textContent?.trim()).toBeTruthy();
    verificar(http);
    fixture.destroy();
  });

  it('TECNICO (conteo ciego): no pide el reporte, no ve el gráfico ni ningún monto', () => {
    const { http, fixture } = montar('TECNICO');
    flushDashboard(http, null);
    fixture.detectChanges();

    http.expectNone((r) => r.url.startsWith(`${API_URL}/reportes`));
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="tendencia-7-dias"]')).toBeNull();
    expect(el.querySelector('app-grafico-resultados')).toBeNull();
    expect(el.textContent ?? '').not.toContain('S/');
    verificar(http);
    fixture.destroy();
  });
});
