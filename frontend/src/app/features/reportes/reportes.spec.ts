import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReportesScreen } from './reportes';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/reportes`;

describe('ReportesScreen (Tarea 18, D-E2-3)', () => {
  let fixture: ComponentFixture<ReportesScreen>;
  let http: HttpTestingController;
  const el = () => fixture.nativeElement as HTMLElement;

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ReportesScreen],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReportesScreen);
  });

  afterEach(() => http.verify());

  it('muestra la etiqueta de reporte interno sin valor tributario y la fila TOTAL del backend (R19)', async () => {
    await render();

    const req = http.expectOne((r) => r.url === `${BASE_URL}/resultados`);
    req.flush({
      desde: '2026-10-01',
      hasta: '2026-10-02',
      agrupar: 'dia',
      nota: 'Reporte interno, sin valor tributario. Montos con IGV incluido.',
      filas: [
        {
          periodo: '2026-10-01',
          ventas: 200,
          costoVentas: 120,
          margenBruto: 80,
          mermasCosto: 5,
          egresos: 15,
          resultado: 60,
        },
        {
          periodo: '2026-10-02',
          ventas: 0,
          costoVentas: 0,
          margenBruto: 0,
          mermasCosto: 0,
          egresos: 0,
          resultado: 0,
        },
      ],
      totales: {
        ventas: 200,
        costoVentas: 120,
        margenBruto: 80,
        mermasCosto: 5,
        egresos: 15,
        resultado: 60,
      },
    });
    await render();

    const texto = el().textContent ?? '';
    expect(texto).toContain('Reporte interno, sin valor tributario');
    expect(texto.toLowerCase()).not.toContain('factura');
    expect(texto).toContain('2026-10-01');
    expect(texto).toContain('2026-10-02');
    expect(texto).toContain('TOTAL');
    expect(texto).toContain('S/ 200.00');
    expect(texto).toContain('S/ 60.00');
  });
});
