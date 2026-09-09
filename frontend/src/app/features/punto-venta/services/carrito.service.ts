import { Injectable, computed, inject, signal } from '@angular/core';
import { PresentacionProducto, Producto } from '../../../core/models/producto.model';
import { ConfigService } from '../../../core/services/config.service';

export interface LineaCarrito {
  key: string; // productoId + '|' + presentacionId
  productoId: string;
  presentacionId: string;
  nombre: string;
  presentacion: string;
  precioUnitario: number;
  cantidad: number;
  alerta: string | null;
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

  agregar(producto: Producto, presentacion: PresentacionProducto): void {
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
          alerta: producto.alertaVencimiento,
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
