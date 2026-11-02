import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { errorInterceptor } from '../../core/interceptors/error.interceptor';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CatalogoScreen } from './catalogo';
import { ProductoCatalogo } from '../../core/models/catalogo.model';
import { PaginaResponse } from '../../core/models/pagina.model';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/productos`;

const PRODUCTO: ProductoCatalogo = {
  id: 5, nombre: 'Paracetamol 500 mg', laboratorio: 'Genfar', categoria: 'Analgésicos', codigoBarras: '7751',
  unidadNombre: 'tableta', activo: true,
  presentaciones: [
    { id: 51, etiqueta: 'Caja', factorConversion: 100, precio: 12.9, activo: true },
    { id: 52, etiqueta: 'Blíster', factorConversion: 10, precio: 1.8, activo: true },
    { id: 53, etiqueta: 'Unidad', factorConversion: 1, precio: 0.2, activo: true },
    { id: 54, etiqueta: 'Sobre', factorConversion: 4, precio: 0.9, activo: false },
  ],
};

function pagina(contenido: ProductoCatalogo[], numero = 0): PaginaResponse<ProductoCatalogo> {
  return { contenido, pagina: numero, tamano: 20, totalElementos: contenido.length, totalPaginas: 3 };
}

describe('CatalogoScreen', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CatalogoScreen],
      // Con el interceptor real: el mensaje del servidor es el que llega al formulario.
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function crear() {
    const fixture = TestBed.createComponent(CatalogoScreen);
    fixture.detectChanges();
    await fixture.whenStable();
    http.expectOne((r) => r.url === `${BASE_URL}/catalogo`).flush(pagina([PRODUCTO]));
    fixture.detectChanges();
    return fixture;
  }

  it('editar: quitar una presentación existente la omite del PUT y avisa que se desactivará; las demás van con su id', async () => {
    const fixture = await crear();
    const pantalla = fixture.componentInstance;

    pantalla.abrirEdicion(PRODUCTO);
    expect(pantalla.desactivadas().map((d) => d.id)).toEqual([54]); // la inactiva no es una fila editable
    const blister = pantalla.filas().find((f) => f.id === 52)!;
    pantalla.quitarFila(blister);
    expect(pantalla.quitadas()).toEqual(['Blíster']);
    pantalla.guardar();

    const req = http.expectOne(`${BASE_URL}/5`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.presentaciones).toEqual([
      { id: 51, etiqueta: 'Caja', factorConversion: 100, precio: 12.9 },
      { id: 53, etiqueta: 'Unidad', factorConversion: 1, precio: 0.2 },
    ]);
    req.flush({ ...PRODUCTO });
    expect(pantalla.formularioAbierto()).toBe(false);
  });

  it('la fila de factor 1 no se puede quitar', async () => {
    const fixture = await crear();
    const pantalla = fixture.componentInstance;

    pantalla.abrirEdicion(PRODUCTO);
    pantalla.quitarFila(pantalla.filas().find((f) => f.esUnidad)!);

    expect(pantalla.filas().some((f) => f.id === 53)).toBe(true);
  });

  it('nuevo: guardar con campos vacíos no llama al servidor y recién entonces muestra los faltantes', async () => {
    const fixture = await crear();
    const pantalla = fixture.componentInstance;

    pantalla.abrirNuevo();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.catalogo__errores')).toBeNull(); // no al abrir

    pantalla.guardar();
    fixture.detectChanges();

    http.expectNone(BASE_URL);
    expect(fixture.nativeElement.querySelector('.catalogo__errores')?.textContent).toContain('Falta el nombre');
  });

  it('un error del servidor al guardar se muestra dentro del formulario', async () => {
    const fixture = await crear();
    const pantalla = fixture.componentInstance;

    pantalla.abrirEdicion(PRODUCTO);
    pantalla.guardar();
    http.expectOne(`${BASE_URL}/5`).flush(
      { error: 'FACTOR_NO_EDITABLE', mensaje: 'El factor de "Caja" no se puede cambiar' },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(pantalla.formularioAbierto()).toBe(true);
    expect(fixture.nativeElement.querySelector('.catalogo__form-modal')?.textContent).toContain('no se puede cambiar');
  });

  it('una carga nueva descarta la respuesta de la anterior (switchMap, [FE-008])', async () => {
    const fixture = await crear();
    const pantalla = fixture.componentInstance;

    pantalla.irAPagina(1);
    pantalla.irAPagina(2);

    const vieja = http.expectOne((r) => r.url === `${BASE_URL}/catalogo` && r.params.get('pagina') === '1');
    const nueva = http.expectOne((r) => r.url === `${BASE_URL}/catalogo` && r.params.get('pagina') === '2');
    expect(vieja.cancelled).toBe(true);
    nueva.flush(pagina([{ ...PRODUCTO, id: 9, nombre: 'Página dos' }], 2));

    expect(pantalla.productos().map((p) => p.nombre)).toEqual(['Página dos']);
  });
});
