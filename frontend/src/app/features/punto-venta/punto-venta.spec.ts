import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { errorInterceptor } from '../../core/interceptors/error.interceptor';
import { Producto } from '../../core/models/producto.model';
import { RELOJ } from '../../core/utils/lector-usb';
import { environment } from '../../../environments/environment';
import { PuntoVentaScreen } from './punto-venta';

const BASE_URL = `${environment.apiUrl}/productos`;
const EAN = '7751234000115';

const PRODUCTO: Producto = {
  id: 7,
  nombre: 'Ibuprofeno 400 mg',
  laboratorio: 'Genfar',
  categoria: 'Analgésicos',
  codigoBarras: EAN,
  estadoVencimiento: 'OK',
  fechaVencimiento: '2027-12-31',
  activo: true,
  requiereReceta: false,
  presentaciones: [
    { id: 71, etiqueta: 'Unidad', factorConversion: 1, unidadNombre: 'tableta', precio: 0.5 },
  ],
};

/**
 * DIF-05 de punta a punta en la pantalla: KeyboardEvent reales sobre el
 * buscador; el tiempo lo da un RELOJ falso que el test avanza a mano.
 */
describe('PuntoVentaScreen — lector USB en el buscador (DIF-05)', () => {
  let http: HttpTestingController;
  let ahora: number;

  beforeEach(() => {
    ahora = 0;
    TestBed.configureTestingModule({
      imports: [PuntoVentaScreen],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: RELOJ, useValue: () => ahora },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function crear() {
    const fixture = TestBed.createComponent(PuntoVentaScreen);
    fixture.detectChanges();
    await fixture.whenStable();
    const campo = fixture.nativeElement.querySelector('#pos-query') as HTMLInputElement;
    return { fixture, pantalla: fixture.componentInstance, campo };
  }

  /** Lo que hace un teclado (o un lector USB) por cada carácter: keydown y, si es un carácter, input insertText. */
  function teclear(campo: HTMLInputElement, texto: string, paso: number): void {
    for (const c of texto) {
      campo.dispatchEvent(new KeyboardEvent('keydown', { key: c, bubbles: true }));
      campo.value += c;
      campo.dispatchEvent(
        new InputEvent('input', { inputType: 'insertText', data: c, bubbles: true }),
      );
      ahora += paso;
    }
    campo.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }),
    );
  }

  function pedidosPorCodigo() {
    return http.match((r) => r.url.startsWith(`${BASE_URL}/codigo/`));
  }

  it('lector (5 ms por tecla): busca el código como escaneo y la línea entra al carrito con ESCANEO', async () => {
    const { fixture, pantalla, campo } = await crear();
    teclear(campo, EAN, 5);

    const [pedido, ...otros] = pedidosPorCodigo();
    expect(otros).toEqual([]);
    expect(pedido.request.url).toBe(`${BASE_URL}/codigo/${EAN}`);
    pedido.flush(PRODUCTO);
    fixture.detectChanges();

    expect(pantalla.query()).toBe(''); // el lector tecleó en el campo: se limpia
    pantalla.agregarPresentacion(PRODUCTO.presentaciones[0]);
    expect(pantalla.carrito.lineas().map((l) => l.origenCaptura)).toEqual(['ESCANEO']);
  });

  it('persona (120 ms por tecla): no busca por código; elegir el resultado da MANUAL (Nota 2)', async () => {
    const { pantalla, campo } = await crear();
    teclear(campo, EAN, 120);
    expect(pedidosPorCodigo()).toEqual([]);
    expect(pantalla.query()).toBe(EAN);

    pantalla.elegirProducto(PRODUCTO);
    pantalla.agregarPresentacion(PRODUCTO.presentaciones[0]);
    expect(pantalla.carrito.lineas().map((l) => l.origenCaptura)).toEqual(['MANUAL']);
  });

  it('un pegado (input que no es insertText) justo antes del Enter anula una ráfaga completa y rápida', async () => {
    const { campo } = await crear();
    for (const c of EAN) {
      campo.dispatchEvent(new KeyboardEvent('keydown', { key: c, bubbles: true }));
      campo.dispatchEvent(
        new InputEvent('input', { inputType: 'insertText', data: c, bubbles: true }),
      );
      ahora += 5;
    }
    campo.dispatchEvent(new InputEvent('input', { inputType: 'insertFromPaste', bubbles: true }));
    campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(pedidosPorCodigo()).toEqual([]);
  });

  it('el escáner simulado ya no existe', async () => {
    const { fixture } = await crear();
    expect(fixture.nativeElement.textContent).not.toContain('Simular escaneo');
    expect(fixture.nativeElement.querySelector('app-lector-camara')).not.toBeNull();
  });
});
