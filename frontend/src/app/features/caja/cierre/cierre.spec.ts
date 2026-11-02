import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CierreComponent } from './cierre';
import { CajaDiaria, MovimientoCaja } from '../../../core/models/caja.model';
import { environment } from '../../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/caja`;

const CAJA_ABIERTA: CajaDiaria = {
  id: 3, fecha: '2026-10-14', usuarioId: 1, usuarioNombre: 'Rosa Quispe', turno: 'Tarde',
  montoApertura: 123.45, horaApertura: '2026-10-14T14:00:00-05:00', horaCierre: null,
  montoContado: null, montoEsperado: null, diferencia: null, semaforoDescuadre: null,
  observaciones: null, abierta: true,
};

function movimiento(id: number, monto: number, afectaEfectivo: boolean): MovimientoCaja {
  return { id, cajaId: 3, tipo: 'venta', descripcion: `Venta #${id}`, nota: null, monto, afectaEfectivo, creadoEn: '2026-10-14T15:30:00-05:00' };
}

/** Todos los "S/ x" que se ven en pantalla. */
function montosEnPantalla(el: HTMLElement): string[] {
  return el.textContent?.match(/S\/\s*[\d.,]+/g) ?? [];
}

describe('CierreComponent — conteo ciego del TECNICO (revisión 2026-10-14)', () => {
  let fixture: ComponentFixture<CierreComponent>;
  let http: HttpTestingController;
  const el = () => fixture.nativeElement as HTMLElement;

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [CierreComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CierreComponent);
    await render();
    http.expectOne(`${BASE_URL}/hoy`).flush(CAJA_ABIERTA);
    await render();
    http.expectOne(`${BASE_URL}/3/resumen-cierre`)
      .flush({ totalVentasDigital: 4.44, cantidadVentas: 2, cantidadMovimientos: 3 });
    http.expectOne((r) => r.url === `${BASE_URL}/3/movimientos`)
      .flush({ contenido: [movimiento(5, 8.88, true), movimiento(6, 4.44, false)], pagina: 0, tamano: 20, totalElementos: 2, totalPaginas: 1 });
    await render();
  });

  afterEach(() => http.verify());

  it('antes de contar, la lista muestra hora, tipo y método, sin montos del efectivo', () => {
    const texto = el().textContent ?? '';
    expect(texto).toContain('Venta #5');
    expect(texto).toContain('Efectivo');
    expect(texto).toContain('Digital');
    // Hora en la zona de la máquina, igual que el componente (no fijar '03:30': depende del TZ de quien corre).
    expect(texto).toContain(new Date('2026-10-14T15:30:00-05:00').toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' }));
    // Ni la apertura ni las ventas ni lo que sumarían.
    expect(texto).not.toContain('123.45');
    expect(texto).not.toContain('8.88');
    expect(texto).not.toContain('131.');
    expect(texto.toLowerCase()).not.toContain('esperado');
    // Únicas cifras: el total digital (decisión 2026-09-08) y los umbrales del semáforo (configuración).
    expect(montosEnPantalla(el())).toEqual(['S/ 4.44', 'S/ 10.00', 'S/ 10.00']);
  });

  it('después del POST la lista se muestra completa, con montos', async () => {
    const input = el().querySelector<HTMLInputElement>('#cierre-contado')!;
    input.value = '131.89';
    input.dispatchEvent(new Event('input'));
    await render();
    [...el().querySelectorAll('button')].find((b) => b.textContent?.includes('Cerrar caja'))!.click();
    await render();
    [...el().querySelectorAll('button')].find((b) => b.textContent?.includes('Sí, cerrar caja'))!.click();

    const post = http.expectOne({ method: 'POST', url: `${BASE_URL}/cerrar` });
    expect(post.request.body.cajaId).toBeUndefined();
    post.flush({ ...CAJA_ABIERTA, abierta: false, montoContado: 131.89, montoEsperado: 132.33, diferencia: -0.44, semaforoDescuadre: 'LEVE' });
    http.match((r) => r.url.includes('/dashboard/')).forEach((r) => r.flush({}));
    await render();

    const texto = el().textContent ?? '';
    expect(texto).toContain('123.45');
    expect(texto).toContain('8.88');
    expect(texto).toContain('Efectivo esperado');
  });
});
