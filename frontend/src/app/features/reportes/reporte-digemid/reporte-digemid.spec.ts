import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReportesScreen } from '../reportes';
import { ROTULO_DIGEMID } from './reporte-digemid';
import { environment } from '../../../../environments/environment';
import { ReporteDigemid } from '../../../core/models/reporte.model';
import { rangoRapido } from '../../../core/utils/fecha-lima.util';

const BASE_URL = `${environment.apiUrl}/reportes`;

function reporte(): ReporteDigemid {
  return {
    desde: '2026-11-01',
    hasta: '2026-11-04',
    nota: ROTULO_DIGEMID + '.',
    lineas: {
      contenido: [
        {
          id: 1, fecha: '2026-11-02T15:30:00Z', ventaId: 501, productoId: 7, producto: 'Amoxicilina 500 mg',
          presentacion: 'Blíster x10', cantidad: 10, unidadNombre: 'cápsula', loteId: 4, loteCodigo: 'AMX-01',
          loteVencimiento: '2027-03-31', recetaNumero: 'RX-77', recetaCmp: '12345', medicoNombre: 'Dra. Ana Torres',
          fechaEmision: '2026-11-01', tecnico: 'Sofía Delgado Ríos',
        },
        {
          id: 2, fecha: '2026-11-03T20:00:00Z', ventaId: 502, productoId: 7, producto: 'Amoxicilina 500 mg',
          presentacion: 'Unidad', cantidad: 2, unidadNombre: 'cápsula', loteId: null, loteCodigo: null,
          loteVencimiento: null, recetaNumero: 'RX-78', recetaCmp: '54321', medicoNombre: null,
          fechaEmision: null, tecnico: 'Sofía Delgado Ríos',
        },
      ],
      pagina: 0,
      tamano: 50,
      totalElementos: 2,
      totalPaginas: 1,
    },
    totalesPorProducto: [{ productoId: 7, producto: 'Amoxicilina 500 mg', unidadNombre: 'cápsula', recetas: 2, unidades: 12 }],
    totales: { lineas: 2, recetas: 2, unidades: 12 },
  };
}

describe('Reportes → pestaña DIGEMID (RF-54)', () => {
  let fixture: ComponentFixture<ReportesScreen>;
  let http: HttpTestingController;
  const el = () => fixture.nativeElement as HTMLElement;

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function chip(texto: string): HTMLElement {
    const c = Array.from(el().querySelectorAll<HTMLElement>('app-chip')).find((x) => x.textContent?.trim() === texto);
    if (!c) throw new Error(`sin chip "${texto}"`);
    return (c.querySelector('button') as HTMLElement | null) ?? c;
  }

  async function abrirDigemid(): Promise<void> {
    await render();
    http.expectOne((r) => r.url === `${BASE_URL}/resultados`).flush(null);
    chip('DIGEMID').click();
    await render();
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

  it('no pide el reporte DIGEMID hasta abrir la pestaña; al abrirla, pide el mes en curso', async () => {
    await render();
    http.expectOne((r) => r.url === `${BASE_URL}/resultados`).flush(null);
    http.expectNone((r) => r.url === `${BASE_URL}/digemid`);

    chip('DIGEMID').click();
    await render();
    const { desde, hasta } = rangoRapido('mes');
    const req = http.expectOne((r) => r.url === `${BASE_URL}/digemid`);
    expect(req.request.params.get('desde')).toBe(desde);
    expect(req.request.params.get('hasta')).toBe(hasta);
    expect(req.request.params.get('pagina')).toBe('0');
    req.flush(reporte());
  });

  it('muestra el rótulo, las líneas con receta, "Sin lote (en revisión)" y los totales por producto', async () => {
    await abrirDigemid();
    http.expectOne((r) => r.url === `${BASE_URL}/digemid`).flush(reporte());
    await render();

    const texto = el().textContent ?? '';
    expect(texto).toContain('Reporte interno de dispensación con receta. No reemplaza los formatos oficiales de DIGEMID');
    expect(texto).toContain('RX-77');
    expect(texto).toContain('AMX-01');
    expect(texto).toContain('31/03/2027');
    expect(texto).toContain('Dra. Ana Torres');
    expect(texto).toContain('Sofía Delgado Ríos');
    expect(texto).toContain('Sin lote (en revisión)');
    const total = el().querySelector('.digemid__fila-total')?.textContent ?? '';
    expect(total).toContain('TOTAL');
    expect(total).toContain('2');
    expect(total).toContain('12');
  });

  it('un acceso rápido vuelve a consultar con sus fechas', async () => {
    await abrirDigemid();
    http.expectOne((r) => r.url === `${BASE_URL}/digemid`).flush(reporte());
    await render();

    chip('Mes anterior').click();
    await render();
    const { desde, hasta } = rangoRapido('mes-anterior');
    const req = http.expectOne((r) => r.url === `${BASE_URL}/digemid`);
    expect(req.request.params.get('desde')).toBe(desde);
    expect(req.request.params.get('hasta')).toBe(hasta);
    req.flush(reporte());
  });

  it('muestra el error del servicio (regla 1 de frontend)', async () => {
    await abrirDigemid();
    http
      .expectOne((r) => r.url === `${BASE_URL}/digemid`)
      .flush({ error: 'RANGO_FECHAS_INVALIDO' }, { status: 400, statusText: 'Bad Request' });
    await render();

    expect(el().querySelector('app-error-banner')?.textContent?.trim()).toBeTruthy();
  });

  it('"Descargar Excel" pide el .xlsx del mismo rango', async () => {
    await abrirDigemid();
    http.expectOne((r) => r.url === `${BASE_URL}/digemid`).flush(reporte());
    await render();

    const boton = Array.from(el().querySelectorAll<HTMLElement>('app-reporte-digemid app-button'))
      .find((b) => b.textContent?.includes('Descargar Excel'));
    (boton?.querySelector('button') as HTMLElement).click();
    await render();
    const { desde, hasta } = rangoRapido('mes');
    const req = http.expectOne((r) => r.url === `${BASE_URL}/digemid/xlsx`);
    expect(req.request.params.get('desde')).toBe(desde);
    expect(req.request.params.get('hasta')).toBe(hasta);
    req.flush(new Blob(['x']));
  });
});
