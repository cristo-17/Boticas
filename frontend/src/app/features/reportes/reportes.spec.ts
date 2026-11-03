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
          etiqueta: '01/10/2026',
          ventas: 200,
          costoVentas: 120,
          margenBruto: 80,
          mermasCosto: 5,
          egresos: 15,
          gastos: 140,
          resultado: 60,
        },
        {
          periodo: '2026-10-02',
          etiqueta: '02/10/2026',
          ventas: 0,
          costoVentas: 0,
          margenBruto: 0,
          mermasCosto: 0,
          egresos: 0,
          gastos: 0,
          resultado: 0,
        },
      ],
      totales: {
        ventas: 200,
        costoVentas: 120,
        margenBruto: 80,
        mermasCosto: 5,
        egresos: 15,
        gastos: 140,
        resultado: 60,
      },
    });
    await render();

    const texto = el().textContent ?? '';
    expect(texto).toContain('Reporte interno, sin valor tributario');
    expect(texto.toLowerCase()).not.toContain('factura');
    expect(texto).toContain('01/10/2026');
    expect(texto).toContain('02/10/2026');
    expect(texto).toContain('S/ 140.00'); // gastos del servidor, no sumados en el cliente
    expect(texto).toContain('Descargar Excel');
    expect(texto).not.toContain('CSV');
    expect(el().querySelector('app-grafico-resultados')).not.toBeNull();
    expect(texto).toContain('TOTAL');
    expect(texto).toContain('S/ 200.00');
    expect(texto).toContain('S/ 60.00');
  });

  function botonPorTexto(texto: string): HTMLElement {
    const candidato = Array.from(el().querySelectorAll('app-chip button, app-chip, button')).find(
      (b) => (b.textContent ?? '').trim() === texto,
    ) as HTMLElement | undefined;
    if (!candidato) throw new Error(`No hay botón "${texto}"`);
    return (candidato.querySelector('button') as HTMLElement | null) ?? candidato;
  }

  it('los accesos rápidos piden el rango calculado con la fecha de Lima; Personalizado muestra desde/hasta', async () => {
    await render();
    http.expectOne((r) => r.url === `${BASE_URL}/resultados`); // Este mes (inicial)
    expect(el().querySelector('#reporte-desde')).toBeNull();

    botonPorTexto('Mes anterior').click();
    await render();
    const req = http.expectOne((r) => r.url === `${BASE_URL}/resultados`);
    const desde = req.request.params.get('desde')!;
    const hasta = req.request.params.get('hasta')!;
    expect(desde.endsWith('-01')).toBe(true);
    expect(desde.slice(0, 7)).toBe(hasta.slice(0, 7));
    expect(fixture.componentInstance.rangoActivo()).toBe('mes-anterior');

    botonPorTexto('Personalizado').click();
    await render();
    http.expectNone((r) => r.url === `${BASE_URL}/resultados`);
    expect(el().querySelector('#reporte-desde')).not.toBeNull();
    expect(el().querySelector('#reporte-hasta')).not.toBeNull();
  });

  it('el chip Semana consulta con agrupar=semana', async () => {
    await render();
    http.expectOne((r) => r.url === `${BASE_URL}/resultados`);
    botonPorTexto('Semana').click();
    await render();
    http.expectOne((r) => r.url === `${BASE_URL}/resultados` && r.params.get('agrupar') === 'semana');
  });

  it('muestra el error del servicio si falla la consulta (regla 1 de frontend)', async () => {
    await render();
    http
      .expectOne((r) => r.url === `${BASE_URL}/resultados`)
      .flush({ error: 'ERROR_INTERNO' }, { status: 500, statusText: 'Error' });
    await render();
    expect(el().querySelector('app-error-banner')?.textContent?.trim()).toBeTruthy();
  });
});
