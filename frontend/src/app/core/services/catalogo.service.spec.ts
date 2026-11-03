import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CatalogoService } from './catalogo.service';
import { ProductoCatalogo, ProductoRequest } from '../models/catalogo.model';
import { PaginaResponse } from '../models/pagina.model';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/productos`;

function producto(id: number, activo = true): ProductoCatalogo {
  return {
    id, nombre: `Producto ${id}`, laboratorio: null, categoria: 'Otros', codigoBarras: `77${id}`,
    unidadNombre: 'tableta', activo, requiereReceta: false,
    presentaciones: [{ id: id * 10, etiqueta: 'Unidad', factorConversion: 1, precio: 0.2, activo: true }],
  };
}

function pagina(...contenido: ProductoCatalogo[]): PaginaResponse<ProductoCatalogo> {
  return { contenido, pagina: 0, tamano: 20, totalElementos: contenido.length, totalPaginas: 1 };
}

const REQUEST: ProductoRequest = {
  nombre: 'Nuevo', laboratorio: null, categoria: 'Otros', codigoBarras: '775', unidadNombre: 'tableta', requiereReceta: true,
  presentaciones: [{ etiqueta: 'Unidad', factorConversion: 1, precio: 0.2 }],
};

describe('CatalogoService', () => {
  let service: CatalogoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CatalogoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listar hace GET a /productos/catalogo con buscar, pagina y tamano, y guarda la página', () => {
    service.listar('para', 2, 20).subscribe();

    const req = http.expectOne((r) => r.url === `${BASE_URL}/catalogo`);
    expect(req.request.params.get('buscar')).toBe('para');
    expect(req.request.params.get('pagina')).toBe('2');
    expect(service.cargando()).toBe(true);
    req.flush(pagina(producto(1)));

    expect(service.productos().map((p) => p.id)).toEqual([1]);
    expect(service.cargando()).toBe(false);
  });

  it('listar en error deja el mensaje en error, apaga cargando en finalize y no toca errorGuardado', () => {
    service.listar().subscribe({ error: () => {} });
    http.expectOne(`${BASE_URL}/catalogo?buscar=&pagina=0&tamano=20`)
      .flush({ error: 'SIN_PERMISO' }, { status: 403, statusText: 'Forbidden' });

    expect(service.error()).not.toBeNull();
    expect(service.cargando()).toBe(false);
    expect(service.errorGuardado()).toBeNull();
  });

  it('crear hace POST y un error del servidor queda en errorGuardado, no en el error del listado', () => {
    service.crear(REQUEST).subscribe({ error: () => {} });

    const req = http.expectOne(BASE_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(REQUEST);
    expect(service.guardando()).toBe(true);
    req.flush({ error: 'CODIGO_BARRAS_DUPLICADO', mensaje: 'El código de barras 775 ya es de otro producto' },
      { status: 409, statusText: 'Conflict' });

    expect(service.guardando()).toBe(false);
    expect(service.errorGuardado()).not.toBeNull();
    expect(service.error()).toBeNull();
  });

  it('cambiarEstado hace PATCH /{id}/estado y reemplaza el producto en la página cargada', () => {
    service.listar().subscribe();
    http.expectOne((r) => r.url === `${BASE_URL}/catalogo`).flush(pagina(producto(1), producto(2)));

    service.cambiarEstado(2, false).subscribe();
    const req = http.expectOne(`${BASE_URL}/2/estado`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ activo: false });
    req.flush(producto(2, false));

    expect(service.productos().find((p) => p.id === 2)?.activo).toBe(false);
    expect(service.productos().find((p) => p.id === 1)?.activo).toBe(true);
  });

  it('actualizar hace PUT /{id}', () => {
    service.actualizar(7, REQUEST).subscribe();
    const req = http.expectOne(`${BASE_URL}/7`);
    expect(req.request.method).toBe('PUT');
    req.flush(producto(7));
    expect(service.guardando()).toBe(false);
  });
});
