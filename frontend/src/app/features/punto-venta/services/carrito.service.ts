import { Injectable, computed, inject, signal } from '@angular/core';
import { PresentacionProducto, Producto } from '../../../core/models/producto.model';
import { OrigenCaptura } from '../../../core/models/venta.model';
import { ConfigService } from '../../../core/services/config.service';
import { textoAlertaVencimiento } from '../../../core/utils/fecha.util';

export interface LineaCarrito {
  key: string; // productoId + '|' + presentacionId
  productoId: number;
  presentacionId: number;
  nombre: string;
  presentacion: string;
  precioUnitario: number;
  cantidad: number;
  alerta: string | null;
  /** Capturado al agregar (D4/Nota 1) — no se reconstruye después. Si el cajero suma más del mismo producto por otro camino, se conserva el origen original. */
  origenCaptura: OrigenCaptura;
}

/** Fallback si /api/config no llegó a cargar todavía (no debería pasar: provideAppInitializer la espera antes de arrancar la app). */
const TASA_IGV_DEFECTO = 0.18;

@Injectable()
export class CarritoService {
  private readonly config = inject(ConfigService);

  private readonly _lineas = signal<LineaCarrito[]>([]);
  readonly lineas = this._lineas.asReadonly();

  readonly vacio = computed(() => this._lineas().length === 0);
  readonly cantidadLineas = computed(() => this._lineas().length);
  readonly unidades = computed(() => this._lineas().reduce((acc, l) => acc + l.cantidad, 0));
  readonly subtotal = computed(() =>
    this._lineas().reduce((acc, l) => acc + l.precioUnitario * l.cantidad, 0),
  );
  readonly igv = computed(() => {
    const tasa = this.config.config()?.igv ?? TASA_IGV_DEFECTO;
    return this.subtotal() - this.subtotal() / (1 + tasa);
  });
  readonly total = computed(() => this.subtotal());

  agregar(producto: Producto, presentacion: PresentacionProducto, origen: OrigenCaptura): void {
    const key = `${producto.id}|${presentacion.id}`;
    this._lineas.update((lineas) => {
      const existente = lineas.find((l) => l.key === key);
      if (existente) {
        return lineas.map((l) => (l.key === key ? { ...l, cantidad: l.cantidad + 1 } : l));
      }
      return [
        ...lineas,
        {
          key,
          productoId: producto.id,
          presentacionId: presentacion.id,
          nombre: producto.nombre,
          presentacion: presentacion.etiqueta,
          precioUnitario: presentacion.precio,
          cantidad: 1,
          // Capturado una sola vez, al agregar: no se recalcula mientras la línea vive en el carrito.
          alerta: textoAlertaVencimiento(producto.estadoVencimiento, producto.fechaVencimiento),
          origenCaptura: origen,
        },
      ];
    });
  }

  incrementar(key: string): void {
    this._lineas.update((lineas) =>
      lineas.map((l) => (l.key === key ? { ...l, cantidad: l.cantidad + 1 } : l)),
    );
  }

  decrementar(key: string): void {
    this._lineas.update((lineas) =>
      lineas.map((l) => (l.key === key ? { ...l, cantidad: Math.max(1, l.cantidad - 1) } : l)),
    );
  }

  quitar(key: string): void {
    this._lineas.update((lineas) => lineas.filter((l) => l.key !== key));
  }

  vaciar(): void {
    this._lineas.set([]);
  }
}
