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
        resultado: 25,
      },
    };
    req.flush(body);

    expect(service.reporte()).toEqual(body);
    expect(service.cargando()).toBe(false);
  });

  it('descargarCsv solicita blob al backend en /api/reportes/resultados/csv', () => {
    let recibido: Blob | null = null;
    service.descargarCsv('2026-05-01', '2026-05-03', 'mes').subscribe((b) => (recibido = b));

    const req = http.expectOne(
      (r) =>
        r.url === `${BASE_URL}/resultados/csv` &&
        r.params.get('desde') === '2026-05-01' &&
        r.params.get('hasta') === '2026-05-03' &&
        r.params.get('agrupar') === 'mes',
    );
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['\uFEFFPeríodo,Ventas (S/)\r\n'], { type: 'text/csv;charset=UTF-8' }));

    expect(recibido).toBeInstanceOf(Blob);
    expect(service.descargandoCsv()).toBe(false);
  });
});
