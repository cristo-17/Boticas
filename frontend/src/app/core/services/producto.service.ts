import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { Producto } from '../models/producto.model';
import { simulate } from './mock-utils';

const CATALOGO_MOCK: Producto[] = [
  {
    id: 'p1',
    nombre: 'Paracetamol 500 mg',
    laboratorio: 'Genfar',
    categoria: 'Analgésicos',
    codigoBarras: '7751234000118',
    alertaVencimiento: null,
    presentaciones: [
      { id: 'p1-caja', etiqueta: 'Caja', detalle: '100 tabletas · lote L-2405A', precio: 12.9 },
      { id: 'p1-blister', etiqueta: 'Blíster', detalle: '10 tabletas', precio: 1.8 },
      { id: 'p1-unidad', etiqueta: 'Unidad', detalle: '1 tableta', precio: 0.2 },
    ],
  },
  {
    id: 'p2',
    nombre: 'Amoxicilina 500 mg',
    laboratorio: 'Portugal',
    categoria: 'Antibióticos',
    codigoBarras: '7751234000217',
    alertaVencimiento: 'Vence en 58 días',
    presentaciones: [
      { id: 'p2-caja', etiqueta: 'Caja', detalle: '50 cápsulas · lote L-2312F', precio: 28.5 },
      { id: 'p2-blister', etiqueta: 'Blíster', detalle: '10 cápsulas', precio: 6.5 },
      { id: 'p2-unidad', etiqueta: 'Unidad', detalle: '1 cápsula', precio: 0.8 },
    ],
  },
  {
    id: 'p3',
    nombre: 'Ibuprofeno 400 mg',
    laboratorio: 'Medifarma',
    categoria: 'Analgésicos',
    codigoBarras: '7751234000316',
    alertaVencimiento: 'Vence en 18 días',
    presentaciones: [
      { id: 'p3-caja', etiqueta: 'Caja', detalle: '100 tabletas · lote L-2401C', precio: 15.0 },
      { id: 'p3-blister', etiqueta: 'Blíster', detalle: '10 tabletas', precio: 2.2 },
      { id: 'p3-unidad', etiqueta: 'Unidad', detalle: '1 tableta', precio: 0.3 },
    ],
  },
  {
    id: 'p4',
    nombre: 'Loratadina 10 mg',
    laboratorio: 'Genfar',
    categoria: 'Antialérgicos',
    codigoBarras: '7751234000415',
    alertaVencimiento: null,
    presentaciones: [
      { id: 'p4-caja', etiqueta: 'Caja', detalle: '30 tabletas · lote L-2502B', precio: 9.9 },
      { id: 'p4-blister', etiqueta: 'Blíster', detalle: '10 tabletas', precio: 3.5 },
      { id: 'p4-unidad', etiqueta: 'Unidad', detalle: '1 tableta', precio: 0.4 },
    ],
  },
  {
    id: 'p5',
    nombre: 'Omeprazol 20 mg',
    laboratorio: 'Unimed',
    categoria: 'Gastrointestinal',
    codigoBarras: '7751234000514',
    alertaVencimiento: null,
    presentaciones: [
      { id: 'p5-caja', etiqueta: 'Caja', detalle: '30 cápsulas · lote L-2604A', precio: 11.5 },
      { id: 'p5-blister', etiqueta: 'Blíster', detalle: '10 cápsulas', precio: 4.2 },
      { id: 'p5-unidad', etiqueta: 'Unidad', detalle: '1 cápsula', precio: 0.5 },
    ],
  },
  {
    id: 'p6',
    nombre: 'Sales de rehidratación',
    laboratorio: 'Farmindustria',
    categoria: 'Otros',
    codigoBarras: '7751234000613',
    alertaVencimiento: null,
    presentaciones: [
      { id: 'p6-caja', etiqueta: 'Caja', detalle: '12 sobres · lote L-2503A', precio: 16.8 },
      { id: 'p6-unidad', etiqueta: 'Unidad', detalle: '1 sobre', precio: 1.6 },
    ],
  },
  {
    id: 'p7',
    nombre: 'Clotrimazol crema 20 g',
    laboratorio: 'Medifarma',
    categoria: 'Dermatológicos',
    codigoBarras: '7751234000712',
    alertaVencimiento: null,
    presentaciones: [{ id: 'p7-unidad', etiqueta: 'Unidad', detalle: 'Tubo 20 g · lote L-2408E', precio: 9.2 }],
  },
  {
    id: 'p8',
    nombre: 'Metformina 850 mg',
    laboratorio: 'Genfar',
    categoria: 'Crónicos',
    codigoBarras: '7751234000811',
    alertaVencimiento: null,
    presentaciones: [{ id: 'p8-caja', etiqueta: 'Caja', detalle: '60 tabletas · lote L-2506A', precio: 18.5 }],
  },
];

@Injectable({ providedIn: 'root' })
export class ProductoService {
  private readonly _resultados = signal<Producto[]>([]);
  readonly resultados = this._resultados.asReadonly();

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  private readonly _masVendidos = signal<Producto[]>([]);
  readonly masVendidos = this._masVendidos.asReadonly();

  /**
   * Producto en pantalla en el modal de "qué presentación vas a vender" —
   * lo puso ahí un escaneo encontrado o una selección manual de la
   * lista de resultados. Vive aquí (no en el componente) para que la
   * regla de propiedad del estado no tenga una excepción según de
   * dónde vino el producto.
   */
  private readonly _seleccionado = signal<Producto | null>(null);
  readonly seleccionado = this._seleccionado.asReadonly();

  // GET /api/productos?buscar={query}
  buscarProductos(query: string): Observable<Producto[]> {
    this._cargando.set(true);
    const q = query.trim().toLowerCase();
    const coincidencias = q
      ? CATALOGO_MOCK.filter((p) => `${p.nombre} ${p.laboratorio} ${p.codigoBarras}`.toLowerCase().includes(q))
      : CATALOGO_MOCK.slice(0, 4);
    return simulate(coincidencias).pipe(
      tap((data) => {
        this._resultados.set(data);
        this._cargando.set(false);
      }),
    );
  }

  // GET /api/productos/codigo/{codigoBarras} — usado por el escáner
  buscarPorCodigoBarras(codigoBarras: string): Observable<Producto | undefined> {
    const encontrado = CATALOGO_MOCK.find((p) => p.codigoBarras === codigoBarras);
    return simulate(encontrado, 900).pipe(tap((data) => this._seleccionado.set(data ?? null)));
  }

  // GET /api/productos/mas-vendidos?turno={turno}
  obtenerMasVendidos(): Observable<Producto[]> {
    return simulate(CATALOGO_MOCK.slice(0, 6)).pipe(tap((data) => this._masVendidos.set(data)));
  }

  /**
   * Lectura síncrona sobre el catálogo completo. La usa VentaService al
   * armar una venta: buscar en `resultados()`/`masVendidos()`.
   */
  obtenerPorId(id: string): Producto | undefined {
    return CATALOGO_MOCK.find((p) => p.id === id);
  }

  /** Selección manual desde la lista de resultados o los más vendidos (no hay fetch: el producto ya está cargado). */
  seleccionar(producto: Producto): void {
    this._seleccionado.set(producto);
  }

  cerrarSeleccion(): void {
    this._seleccionado.set(null);
  }
}
