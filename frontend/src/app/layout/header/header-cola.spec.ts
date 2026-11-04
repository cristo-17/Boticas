import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HeaderColaComponent } from './header-cola';
import { ColaVentasService } from '../../core/services/cola-ventas.service';
import { ConexionService } from '../../core/services/conexion.service';
import { EstadoConexion } from '../../core/models/conexion.model';
import { EntradaCola } from '../../core/models/offline.model';
import { catalogoFalso, colaFalsa } from './cola-falsa.testing';
import { CatalogoOfflineService } from '../../core/services/catalogo-offline.service';
import { ConfigService } from '../../core/services/config.service';
import { ConfigNegocio } from '../../core/models/config.model';

function entrada(clave: string): EntradaCola {
  return {
    claveIdempotencia: clave,
    orden: 1,
    usuarioId: 1,
    usuarioNombre: 'Rosa Quispe',
    boticaId: 1,
    cajaId: 10,
    horaDispositivo: '2026-11-03T14:05:00-05:00',
    sinConexion: true,
    estado: 'PENDIENTE',
    ultimoError: null,
    cuerpo: { claveIdempotencia: clave, items: [], metodoPago: 'efectivo' },
    productos: ['Paracetamol 500 mg × 1 Caja'],
    unidadesPorProducto: {},
  };
}

describe('HeaderColaComponent (Tarea 22, §7)', () => {
  let estado: ReturnType<typeof signal<EstadoConexion>>;
  let propias: ReturnType<typeof signal<EntradaCola[]>>;
  let catalogo: ReturnType<typeof catalogoFalso>;
  let descargas: number;
  let config: ReturnType<typeof signal<ConfigNegocio | null>>;

  beforeEach(() => {
    estado = signal<EstadoConexion>('online');
    propias = signal<EntradaCola[]>([]);
    descargas = 0;
    catalogo = { ...catalogoFalso(), descargar: () => ({ subscribe: () => void descargas++ }) };
    config = signal<ConfigNegocio | null>(null);
    TestBed.configureTestingModule({
      imports: [HeaderColaComponent],
      providers: [
        { provide: ConexionService, useValue: { estado, enLinea: () => estado() !== 'offline', comprobar: async () => true } },
        { provide: ColaVentasService, useValue: colaFalsa(propias) },
        { provide: CatalogoOfflineService, useValue: catalogo },
        { provide: ConfigService, useValue: { config } },
      ],
    });
  });

  it('la etiqueta sigue al estado real y suma cuántas ventas propias faltan enviar', () => {
    const c = TestBed.createComponent(HeaderColaComponent).componentInstance;
    expect(c.etiqueta()).toBe('En línea');
    estado.set('offline');
    expect(c.etiqueta()).toBe('Sin conexión');
    propias.set([entrada('a'), entrada('b')]);
    expect(c.etiqueta()).toBe('Sin conexión · 2 sin enviar');
    estado.set('sync');
    expect(c.etiqueta()).toBe('Enviando… · 2 sin enviar');
  });

  it('el panel lista productos y estado SIN montos (conteo ciego)', () => {
    propias.set([entrada('a')]);
    const fixture = TestBed.createComponent(HeaderColaComponent);
    fixture.componentInstance.alternar();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const fila = el.querySelector('[data-testid="fila-cola"]') as HTMLElement;
    expect(fila.textContent).toContain('Paracetamol 500 mg');
    expect(fila.textContent).toContain('Esperando conexión');
    expect(el.querySelector('.header-cola__panel')?.textContent).not.toContain('S/');
  });

  describe('catálogo para vender sin conexión y versión', () => {
    function abrir() {
      const fixture = TestBed.createComponent(HeaderColaComponent);
      fixture.componentInstance.alternar();
      fixture.detectChanges();
      return { fixture, el: fixture.nativeElement as HTMLElement };
    }

    it('sin foto guardada lo dice; con foto muestra cuántos productos y hace cuánto se bajó', () => {
      const { fixture, el } = abrir();
      const bloque = () => el.querySelector('[data-testid="catalogo-offline"]')?.textContent ?? '';
      expect(bloque()).toContain('Catálogo para vender sin conexión:');
      expect(bloque()).toContain('todavía no se descargó en este equipo');

      catalogo.cantidadProductos.set(51);
      catalogo.generadoEn.set(new Date(Date.now() - 7.5 * 60_000).toISOString());
      fixture.detectChanges();
      expect(bloque()).toContain('51 productos · actualizado hace 7 min');
    });

    it('"Actualizar ahora" baja la foto; sin conexión o mientras baja, el botón queda deshabilitado', () => {
      const { fixture, el } = abrir();
      const boton = () =>
        [...el.querySelectorAll('button')].find((b) => /Actualiz/.test(b.textContent ?? '')) as HTMLButtonElement;
      boton().click();
      expect(descargas).toBe(1);

      catalogo.actualizando.set(true);
      fixture.detectChanges();
      expect(boton().disabled).toBe(true);
      expect(boton().textContent).toContain('Actualizando…');

      catalogo.actualizando.set(false);
      estado.set('offline');
      fixture.detectChanges();
      expect(boton().disabled).toBe(true);
    });

    it('el error de la descarga se ve en el panel (regla 1 del frontend)', () => {
      const { fixture, el } = abrir();
      catalogo.error.set('No se pudo actualizar el catálogo: sin conexión con el servidor.');
      fixture.detectChanges();
      expect(el.querySelector('[role="alert"]')?.textContent).toContain('No se pudo actualizar el catálogo');
    });

    it('muestra la versión de la app y la del servidor (commit corto)', () => {
      config.set({
        igv: 0.18, umbralStockBajo: 15, descuadreLeve: 10, motivosMerma: [], motivosQueRequierenObservacion: [],
        motivosEgreso: [], vencimiento: { criticoDias: 30, advertenciaDias: 90 },
        version: '9627ec3a43676b9c136b5e8c0372cb097933650f',
      });
      const { el } = abrir();
      expect(el.querySelector('[data-testid="version-app"]')?.textContent).toContain('app local · servidor 9627ec3');
    });
  });
});
