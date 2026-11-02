import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
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
