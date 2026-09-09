import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { NuevaVentaRequest, Venta } from '../models/venta.model';
import { AuthService } from './auth.service';
import { ConexionService } from './conexion.service';
import { ConfigService } from './config.service';
import { ProductoService } from './producto.service';
import { nextId, simulate } from './mock-utils';

/** Fallback si /api/config no llegó a cargar todavía (no debería pasar: provideAppInitializer la espera antes de arrancar la app). */
const TASA_IGV_DEFECTO = 0.18;

@Injectable({ providedIn: 'root' })
export class VentaService {
  private readonly auth = inject(AuthService);
  private readonly conexion = inject(ConexionService);
  private readonly config = inject(ConfigService);
  private readonly productoService = inject(ProductoService);

  private readonly _ventasDelDia = signal<Venta[]>([]);
  readonly ventasDelDia = this._ventasDelDia.asReadonly();

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  readonly totalVentasDelDia = computed(() =>
    this._ventasDelDia().reduce((acc, v) => acc + v.total, 0),
  );

  // GET /api/ventas?fecha=hoy
  listarVentasDelDia(): Observable<Venta[]> {
    this._cargando.set(true);
    return simulate(this._ventasDelDia()).pipe(tap(() => this._cargando.set(false)));
  }

  // POST /api/ventas
  registrarVenta(request: NuevaVentaRequest): Observable<Venta> {
    this._cargando.set(true);
    const items = request.items.map((linea) => {
      const producto = this.productoService.obtenerPorId(linea.productoId);
      const presentacion = producto?.presentaciones.find((p) => p.id === linea.presentacionId);
      return {
        productoId: linea.productoId,
        presentacionId: linea.presentacionId,
        nombre: producto?.nombre ?? linea.productoId,
        presentacion: presentacion?.etiqueta ?? '',
        precioUnitario: presentacion?.precio ?? 0,
        cantidad: linea.cantidad,
      };
    });
    const tasaIgv = this.config.config()?.igv ?? TASA_IGV_DEFECTO;
    const subtotal = items.reduce((acc, i) => acc + i.precioUnitario * i.cantidad, 0);
    const igv = subtotal - subtotal / (1 + tasaIgv);
    const offline = this.conexion.estado() === 'offline';
    const venta: Venta = {
      id: nextId('v'),
      fecha: new Date().toISOString(),
      usuarioId: this.auth.usuarioActual()?.id ?? '',
      items,
      subtotal,
      igv,
      total: subtotal,
      metodoPago: request.metodoPago,
      sincronizada: !offline,
    };
    return simulate(venta, 500).pipe(
      tap(() => {
        this._ventasDelDia.update((all) => [venta, ...all]);
        if (offline) this.conexion.marcarPendiente();
        this._cargando.set(false);
      }),
    );
  }
}
