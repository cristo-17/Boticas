import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CierreAdminComponent } from './cierre-admin';
import { CajaDiaria } from '../../../core/models/caja.model';
import { environment } from '../../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/caja`;

function cajaAbierta(id: number, nombre: string): CajaDiaria {
  return {
    id, fecha: '2026-10-14', usuarioId: 10 + id, usuarioNombre: nombre, turno: 'Tarde',
    montoApertura: 123.45, horaApertura: '2026-10-14T14:00:00-05:00', horaCierre: null,
    montoContado: null, montoEsperado: null, diferencia: null, semaforoDescuadre: null,
    observaciones: null, abierta: true,
  };
}

describe('CierreAdminComponent — el ADMINISTRADOR cierra una caja ajena (D-E2-1)', () => {
  let fixture: ComponentFixture<CierreAdminComponent>;
  let http: HttpTestingController;

  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const boton = (etiqueta: string) =>
    [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find((b) => b.textContent?.trim().includes(etiqueta))!;

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [CierreAdminComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CierreAdminComponent);
    await render();
    http.expectOne((r) => r.method === 'GET' && r.url === `${BASE_URL}/abiertas`)
      .flush({ contenido: [cajaAbierta(7, 'Rosa Quispe')], pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1 });
    http.match((r) => r.url.includes('/dashboard/')).forEach((r) => r.flush({}));
    await render();
  });

  afterEach(() => http.verify());

  it('lista las cajas abiertas de la botica sin mostrar ningún monto', () => {
    expect(texto()).toContain('Rosa Quispe');
    expect(texto()).not.toContain('S/');
    expect(texto().toLowerCase()).not.toContain('esperado');
  });

  it('elige una caja, cuenta a ciegas y cierra ESA caja (cajaId en el POST)', async () => {
    boton('Rosa Quispe').click();
    await render();
    expect(texto().toLowerCase()).not.toContain('esperado');

    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#cierre-contado')!;
    input.value = '95.50';
    input.dispatchEvent(new Event('input'));
    await render();
    boton('Cerrar caja').click();
    await render();
    expect(texto().toLowerCase()).not.toContain('esperado');
    boton('Sí, cerrar caja').click();

    const post = http.expectOne({ method: 'POST', url: `${BASE_URL}/cerrar` });
    expect(post.request.body).toEqual({ montoContado: 95.5, cajaId: 7 });
    post.flush({ ...cajaAbierta(7, 'Rosa Quispe'), abierta: false, montoContado: 95.5, montoEsperado: 100, diferencia: -4.5, semaforoDescuadre: 'LEVE' });
    http.match((r) => r.url.includes('/dashboard/')).forEach((r) => r.flush({}));
    await render();

    // Recién con la respuesta del POST aparece el esperado.
    expect(texto()).toContain('Efectivo esperado');
    expect(texto()).toContain('100.00');
    expect(texto()).toContain('Rosa Quispe');
  });
});
