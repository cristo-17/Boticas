import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { CarritoService } from './carrito.service';
import { ConfigService } from '../../../core/services/config.service';
import { Producto } from '../../../core/models/producto.model';
import vectores from '../../../../../../test-vectores/dinero.json';

/**
 * DIF-07 (Tarea 0B-B) y DECISIONES 2026-10-14 (R19): la vista previa del
 * carrito solo se acepta si da EXACTAMENTE lo mismo que el servidor con los
 * casos compartidos. VentaDineroTest (JUnit) lee el mismo archivo.
 */
describe('CarritoService — vectores de dinero compartidos con el servidor', () => {
  function carritoCon(items: { cantidad: number; precioPresentacion: string }[]): CarritoService {
    TestBed.configureTestingModule({
      providers: [
        CarritoService,
        { provide: ConfigService, useValue: { config: signal({ igv: Number(vectores.igvTasa) }) } },
      ],
    });
    const carrito = TestBed.inject(CarritoService);
    items.forEach((item, i) => {
      const producto = { id: i + 1, nombre: `P${i + 1}`, estadoVencimiento: null, fechaVencimiento: null } as unknown as Producto;
      const presentacion = { id: 100 + i, etiqueta: 'Caja', factorConversion: 1, unidadNombre: 'u', precio: Number(item.precioPresentacion) };
      carrito.agregar(producto, presentacion, 'BUSQUEDA');
      for (let n = 1; n < item.cantidad; n++) carrito.incrementar(`${producto.id}|${presentacion.id}`);
    });
    return carrito;
  }

  afterEach(() => TestBed.resetTestingModule());

  it('el archivo tiene casos', () => {
    expect(vectores.casos.length).toBeGreaterThan(0);
  });

  for (const caso of vectores.casos) {
    it(caso.nombre, () => {
      const carrito = carritoCon(caso.items);
      expect(carrito.total()).toBe(Number(caso.esperado.total));
      expect(carrito.opGravada()).toBe(Number(caso.esperado.opGravada));
      expect(carrito.igv()).toBe(Number(caso.esperado.igv));
    });
  }

  it('Tarea 20: productosConReceta lista sin repetir los productos que piden receta', () => {
    const carrito = carritoCon([]);
    const amox = { id: 7, nombre: 'Amoxicilina', estadoVencimiento: null, fechaVencimiento: null, requiereReceta: true } as unknown as Producto;
    const vit = { id: 8, nombre: 'Vitamina C', estadoVencimiento: null, fechaVencimiento: null, requiereReceta: false } as unknown as Producto;
    const caja = { id: 70, etiqueta: 'Caja', factorConversion: 10, unidadNombre: 'cápsula', precio: 9 };
    const unidad = { id: 71, etiqueta: 'Unidad', factorConversion: 1, unidadNombre: 'cápsula', precio: 1 };
    carrito.agregar(vit, { ...unidad, id: 80 }, 'BUSQUEDA');
    expect(carrito.productosConReceta()).toEqual([]);
    carrito.agregar(amox, caja, 'BUSQUEDA');
    carrito.agregar(amox, unidad, 'ESCANEO');
    expect(carrito.productosConReceta()).toEqual(['Amoxicilina']);
    expect(carrito.lineas().find((l) => l.productoId === 7)?.requiereReceta).toBe(true);
  });

  it('vaciar deja el carrito sin líneas y los montos en cero', () => {
    const carrito = carritoCon([{ cantidad: 2, precioPresentacion: '5.00' }]);
    carrito.vaciar();
    expect(carrito.vacio()).toBe(true);
    expect(carrito.total()).toBe(0);
    expect(carrito.opGravada()).toBe(0);
    expect(carrito.igv()).toBe(0);
  });
});
