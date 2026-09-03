import { Injectable, computed, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { FiltroLotes, Lote, NuevoLoteRequest } from '../models/lote.model';
import { diasHasta, estadoFefo } from '../utils/fecha.util';
import { nextId, simulate } from './mock-utils';

const LOTES_MOCK: Lote[] = [
  {
    id: 'l1',
    productoId: 'p1',
    productoNombre: 'Paracetamol 500 mg x100',
    categoria: 'Analgésicos',
    codigo: 'L-2405A',
    fechaVencimiento: '2027-03-12',
    stock: 240,
    ubicacion: 'A-2',
    precioUnitario: 12.9,
  },
  {
    id: 'l2',
    productoId: 'p6',
    productoNombre: 'Sales de rehidratación x12',
    categoria: 'Otros',
    codigo: 'L-2503A',
    fechaVencimiento: '2026-12-02',
    stock: 54,
    ubicacion: 'D-1',
    precioUnitario: 16.8,
  },
  {
    id: 'l3',
    productoId: 'p2',
    productoNombre: 'Amoxicilina 500 mg x50',
    categoria: 'Antibióticos',
    codigo: 'L-2312F',
    fechaVencimiento: '2026-10-30',
    stock: 38,
    ubicacion: 'B-1',
    precioUnitario: 28.5,
  },
  {
    id: 'l4',
    productoId: 'p7',
    productoNombre: 'Clotrimazol crema 20 g',
    categoria: 'Dermatológicos',
    codigo: 'L-2408E',
    fechaVencimiento: '2026-11-18',
    stock: 21,
    ubicacion: 'C-1',
    precioUnitario: 9.2,
  },
  {
    id: 'l5',
    productoId: 'p3',
    productoNombre: 'Ibuprofeno 400 mg x100',
    categoria: 'Analgésicos',
    codigo: 'L-2401C',
    fechaVencimiento: '2026-09-20',
    stock: 12,
    ubicacion: 'A-4',
    precioUnitario: 15.0,
  },
  {
    id: 'l6',
    productoId: 'p5',
    productoNombre: 'Omeprazol 20 mg x30',
    categoria: 'Gastrointestinal',
    codigo: 'L-2311D',
    fechaVencimiento: '2026-08-15',
    stock: 7,
    ubicacion: 'B-2',
    precioUnitario: 11.5,
  },
  {
    id: 'l7',
    productoId: 'p4',
    productoNombre: 'Loratadina 10 mg x30',
    categoria: 'Antialérgicos',
    codigo: 'L-2502B',
    fechaVencimiento: '2027-06-05',
    stock: 96,
    ubicacion: 'C-3',
    precioUnitario: 9.9,
  },
  {
    id: 'l8',
    productoId: 'p8',
    productoNombre: 'Metformina 850 mg x60',
    categoria: 'Crónicos',
    codigo: 'L-2506A',
    fechaVencimiento: '2027-04-22',
    stock: 130,
    ubicacion: 'A-1',
    precioUnitario: 18.5,
  },
];

const UMBRAL_STOCK_BAJO = 15;

@Injectable({ providedIn: 'root' })
export class InventarioService {
  private readonly _todosLosLotes = signal<Lote[]>(LOTES_MOCK);

  private readonly _lotes = signal<Lote[]>([]);
  readonly lotes = this._lotes.asReadonly();

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  /** Categorías disponibles sobre el catálogo completo, no sobre el filtro activo. */
  readonly categorias = computed(() => [
    'Todas',
    ...Array.from(new Set(this._todosLosLotes().map((l) => l.categoria))),
  ]);

  // GET /api/lotes?categoria=&vencimiento=&stockBajo=
  listarLotes(filtro: FiltroLotes = {}): Observable<Lote[]> {
    this._cargando.set(true);
    this._error.set(null);
    const filtrados = this._todosLosLotes()
      .filter((l) => {
        const okCategoria = !filtro.categoria || filtro.categoria === 'Todas' || l.categoria === filtro.categoria;
        const okStockBajo = !filtro.soloStockBajo || l.stock <= UMBRAL_STOCK_BAJO;
        const dias = diasHasta(l.fechaVencimiento);
        const okVencimiento =
          !filtro.vencimiento ||
          filtro.vencimiento === 'todos' ||
          (filtro.vencimiento === 'ok' && dias > 90) ||
          (filtro.vencimiento === 'pronto' && estadoFefo(dias) === 'pronto') ||
          (filtro.vencimiento === 'critico' && dias < 30);
        return okCategoria && okStockBajo && okVencimiento;
      })
      .sort((a, b) => diasHasta(a.fechaVencimiento) - diasHasta(b.fechaVencimiento));
    return simulate(filtrados).pipe(
      tap((data) => {
        this._lotes.set(data);
        this._cargando.set(false);
      }),
    );
  }

  // GET /api/lotes?productoNombre= — usado por el selector de producto en Registrar merma
  listarLotesDeProducto(productoNombre: string): Observable<Lote[]> {
    return simulate(this._todosLosLotes().filter((l) => l.productoNombre.startsWith(productoNombre)));
  }

  // POST /api/lotes
  registrarLote(request: NuevoLoteRequest): Observable<Lote> {
    const producto = this._todosLosLotes().find((l) => l.productoId === request.productoId);
    const lote: Lote = {
      id: nextId('l'),
      productoId: request.productoId,
      productoNombre: producto?.productoNombre ?? request.productoId,
      categoria: producto?.categoria ?? '',
      codigo: request.codigo,
      fechaVencimiento: request.fechaVencimiento,
      stock: request.stock,
      ubicacion: request.ubicacion,
      precioUnitario: request.precioUnitario,
    };
    return simulate(lote).pipe(tap(() => this._todosLosLotes.update((all) => [...all, lote])));
  }

  /** Lectura síncrona sobre el catálogo completo (no el filtrado), para que MermaService valide contra el lote real sin depender de qué filtro tenga puesto la pantalla de inventario. */
  obtenerLotePorId(loteId: string): Lote | undefined {
    return this._todosLosLotes().find((l) => l.id === loteId);
  }

  /** Descuenta stock de un lote (usado por MermaService al confirmar una baja). No es un endpoint propio: viaja dentro de POST /api/mermas. */
  descontarStock(loteId: string, cantidad: number): void {
    this._todosLosLotes.update((all) =>
      all.map((l) => (l.id === loteId ? { ...l, stock: Math.max(0, l.stock - cantidad) } : l)),
    );
  }
}
