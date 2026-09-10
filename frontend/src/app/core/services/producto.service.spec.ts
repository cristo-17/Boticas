import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProductoService } from './producto.service';
import { Producto } from '../models/producto.model';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/productos`;

function productoDePrueba(id: number): Producto {
  return {
    id,
    nombre: 'Paracetamol 500 mg',
    laboratorio: 'Genfar',
    categoria: 'Analgésicos',
    codigoBarras: '7751234000118',
    estadoVencimiento: 'OK',
    fechaVencimiento: '2027-01-01',
    presentaciones: [],
  };
}

describe('ProductoService', () => {
  let service: ProductoService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('buscarProductos hace GET a /productos?buscar=<query> y actualiza el signal resultados', () => {
    const encontrados = [productoDePrueba(1)];

    expect(service.cargando()).toBe(false);
    service.buscarProductos('paraceta').subscribe();

    const req = httpMock.expectOne((r) => r.url === BASE_URL && r.params.get('buscar') === 'paraceta');
    expect(req.request.method).toBe('GET');
    expect(service.cargando()).toBe(true); // se prende ANTES de que la respuesta llegue

    req.flush(encontrados);

    expect(service.resultados()).toEqual(encontrados);
    expect(service.cargando()).toBe(false); // se apaga en finalize(), no en tap()
    expect(service.error()).toBeNull();
  });

  it('buscarProductos en error deja el mensaje traducido en el signal error y apaga cargando', () => {
    service.buscarProductos('x').subscribe({ error: () => {} });

    const req = httpMock.expectOne((r) => r.url === BASE_URL);
    req.flush({ error: 'ERROR_INTERNO' }, { status: 500, statusText: 'Internal Server Error' });

    expect(service.error()).toBeTruthy();
    expect(service.cargando()).toBe(false);
  });

  it('buscarPorCodigoBarras hace GET a /productos/codigo/{codigo} y guarda el seleccionado', () => {
    const producto = productoDePrueba(7);

    service.buscarPorCodigoBarras('7751234000118').subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/codigo/7751234000118`);
    expect(req.request.method).toBe('GET');
    req.flush(producto);

    expect(service.seleccionado()).toEqual(producto);
    expect(service.obtenerPorId(7)).toEqual(producto);
  });

  it('obtenerMasVendidos hace GET a /productos/mas-vendidos y actualiza el signal masVendidos', () => {
    const masVendidos = [productoDePrueba(2), productoDePrueba(3)];

    service.obtenerMasVendidos().subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/mas-vendidos`);
    expect(req.request.method).toBe('GET');
    req.flush(masVendidos);

    expect(service.masVendidos()).toEqual(masVendidos);
  });
});
