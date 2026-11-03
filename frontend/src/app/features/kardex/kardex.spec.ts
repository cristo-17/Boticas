import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { KardexScreen } from './kardex';
import { Kardex } from '../../core/models/kardex.model';
import { environment } from '../../../environments/environment';

const URL_KARDEX = `${environment.apiUrl}/kardex`;

function kardex(parcial: Partial<Kardex['resumen']> = {}): Kardex {
  return {
    productoId: 7,
    productoNombre: 'Amoxicilina 500 mg',
    unidadNombre: 'cápsula',
    requiereReceta: true,
    activo: true,
    desde: null,
    hasta: null,
    resumen: { saldoInicial: 0, entradas: 100, salidas: 14, saldoFinal: 86, stockActual: 86, movimientos: 3, ...parcial },
    movimientos: {
      contenido: [
        { id: 1, fecha: '2026-10-01T14:00:00Z', tipo: 'ENTRADA', entradaInicial: true, cantidad: 100, saldo: 100,
          loteId: 3, loteCodigo: 'A-100', ventaId: null, mermaId: null, recetaNumero: null, usuario: null },
        { id: 2, fecha: '2026-10-02T15:30:00Z', tipo: 'SALIDA_RECETA', entradaInicial: false, cantidad: -10, saldo: 90,
          loteId: 3, loteCodigo: 'A-100', ventaId: 41, mermaId: null, recetaNumero: 'R-001', usuario: 'Rosa Quispe' },
        { id: 3, fecha: '2026-10-03T16:00:00Z', tipo: 'MERMA', entradaInicial: false, cantidad: -4, saldo: 86,
          loteId: 3, loteCodigo: 'A-100', ventaId: null, mermaId: 5, recetaNumero: null, usuario: 'Rosa Quispe' },
      ],
      pagina: 0,
      tamano: 50,
      totalElementos: 3,
      totalPaginas: 1,
    },
  };
}

/** Tarea 20: Kardex por producto (solo ADMINISTRADOR; la ruta tiene rolGuard). */
describe('KardexScreen', () => {
  let fixture: ComponentFixture<KardexScreen>;
  let http: HttpTestingController;
  const el = () => fixture.nativeElement as HTMLElement;

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function configurar(queryParams: Record<string, string>): void {
    TestBed.configureTestingModule({
      imports: [KardexScreen],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(KardexScreen);
  }

  afterEach(() => http.verify());

  it('sin producto no consulta nada y pide elegir uno', async () => {
    configurar({});
    await render();
    http.expectNone((r) => r.url === URL_KARDEX);
    expect(el().textContent).toContain('Elige un producto');
  });

  it('con ?productoId carga el Kardex: rótulo de la migración, receta, saldo del servidor y "cuadra"', async () => {
    configurar({ productoId: '7' });
    await render();

    const req = http.expectOne((r) => r.url === URL_KARDEX);
    expect(req.request.params.get('productoId')).toBe('7');
    expect(req.request.params.has('desde')).toBe(false);
    req.flush(kardex());
    await render();

    const texto = el().textContent ?? '';
    expect(texto).toContain('Amoxicilina 500 mg');
    expect(texto).toContain('Entrada inicial (registrada por la migración)');
    expect(texto).toContain('Salida con receta');
    expect(texto).toContain('Venta #41 · Receta R-001');
    expect(texto).toContain('Merma #5');
    expect(texto).toContain('coincide con el stock de los lotes');
    // Fechas en hora de Lima: 2026-10-02T15:30Z = 10:30 en Lima.
    expect(texto).toContain('02/10/2026 10:30');
  });

  it('si el saldo final no coincide con el stock de los lotes, lo dice', async () => {
    configurar({ productoId: '7' });
    await render();
    http.expectOne((r) => r.url === URL_KARDEX).flush(kardex({ saldoFinal: 80, stockActual: 86 }));
    await render();
    expect(el().textContent).toContain('no coincide con el stock de los lotes');
  });

  it('muestra el error del servidor (regla de frontend 1)', async () => {
    configurar({ productoId: '999' });
    await render();
    http.expectOne((r) => r.url === URL_KARDEX).flush(
      { error: 'PRODUCTO_NO_ENCONTRADO', mensaje: 'El producto indicado no existe' },
      { status: 404, statusText: 'Not Found' },
    );
    await render();
    expect(el().querySelector('app-error-banner')?.textContent).toContain('No se pudo cargar el Kardex');
  });
});
