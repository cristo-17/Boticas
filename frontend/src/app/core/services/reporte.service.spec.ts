import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReporteService } from './reporte.service';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/reportes`;

describe('ReporteService (Tarea 18)', () => {
  let service: ReporteService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReporteService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('obtenerResultados envía desde, hasta y agrupar y actualiza la señal reporte', () => {
    service.obtenerResultados('2026-05-01', '2026-05-03', 'dia').subscribe();

    const req = http.expectOne(
      (r) =>
        r.url === `${BASE_URL}/resultados` &&
        r.params.get('desde') === '2026-05-01' &&
        r.params.get('hasta') === '2026-05-03' &&
        r.params.get('agrupar') === 'dia',
    );
    const body = {
      desde: '2026-05-01',
      hasta: '2026-05-03',
      agrupar: 'dia' as const,
      nota: 'Reporte interno, sin valor tributario. Montos con IGV incluido.',
      filas: [],
      totales: {
        ventas: 120,
        costoVentas: 80,
        margenBruto: 40,
        mermasCosto: 5,
        egresos: 10,
        gastos: 95,
        resultado: 25,
      },
    };
    req.flush(body);

    expect(service.reporte()).toEqual(body);
    expect(service.cargando()).toBe(false);
  });

  it('descargarExcel pide blob a /api/reportes/resultados/xlsx con agrupar=semana', () => {
    let recibido: Blob | null = null;
    service.descargarExcel('2026-11-03', '2026-11-12', 'semana').subscribe((b) => (recibido = b));
    expect(service.descargandoExcel()).toBe(true);

    const req = http.expectOne(
      (r) =>
        r.url === `${BASE_URL}/resultados/xlsx` &&
        r.params.get('desde') === '2026-11-03' &&
        r.params.get('hasta') === '2026-11-12' &&
        r.params.get('agrupar') === 'semana',
    );
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['PK'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));

    expect(recibido).toBeInstanceOf(Blob);
    expect(service.descargandoExcel()).toBe(false);
    expect(ReporteService.nombreArchivoExcel('2026-11-03', '2026-11-12')).toBe(
      'reporte-resultados_2026-11-03_2026-11-12.xlsx',
    );
  });

  it('obtenerTendencia usa agrupar=dia y su propia señal, sin pisar el reporte de la pantalla', () => {
    service.obtenerTendencia('2026-10-27', '2026-11-02').subscribe();
    const req = http.expectOne(
      (r) =>
        r.url === `${BASE_URL}/resultados` &&
        r.params.get('desde') === '2026-10-27' &&
        r.params.get('hasta') === '2026-11-02' &&
        r.params.get('agrupar') === 'dia',
    );
    req.flush({ desde: '2026-10-27', hasta: '2026-11-02', agrupar: 'dia', nota: '', filas: [], totales: {} });

    expect(service.tendencia()?.desde).toBe('2026-10-27');
    expect(service.reporte()).toBeNull();
    expect(service.cargandoTendencia()).toBe(false);
  });

  it('un error de la tendencia va a errorTendencia, no al error de la pantalla Resultados', () => {
    service.obtenerTendencia('2026-10-27', '2026-11-02').subscribe({ error: () => {} });
    http
      .expectOne((r) => r.url === `${BASE_URL}/resultados`)
      .flush({ error: 'ERROR_INTERNO' }, { status: 500, statusText: 'Error' });

    expect(service.errorTendencia()).toBeTruthy();
    expect(service.error()).toBeNull();
    expect(service.cargandoTendencia()).toBe(false);
  });
});
