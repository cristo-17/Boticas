import { Producto } from './producto.model';
import { NuevaVentaRequest, RevisionVenta } from './venta.model';

/** Tarea 22: fecha y stock de un lote vendible, sin id ni costo (GET /api/productos/catalogo-offline). */
export interface LoteVendible {
  fechaVencimiento: string; // yyyy-MM-dd
  stock: number;
}

/** Tarea 22: Producto de mostrador más sus lotes vendibles. */
export interface ProductoOffline extends Producto {
  lotesVendibles: LoteVendible[];
}

/** Tarea 22: la foto del catálogo que el dispositivo guarda para vender sin conexión (§1). */
export interface CatalogoOffline {
  boticaId: number;
  generadoEn: string; // hora del servidor
  productos: ProductoOffline[];
}

/**
 * Tarea 22 (§3): una venta en la cola de IndexedDB. Se escribe ANTES del
 * primer envío, con la claveIdempotencia ya generada: si el teléfono se
 * apaga o la red se corta en ese momento, la venta ya está guardada.
 * - sinConexion: false mientras el cajero espera la respuesta en línea; true
 *   desde que se cobró sin respuesta (la venta ya ocurrió en el mostrador).
 * - DETENIDA: el servidor la rechazó por algo que no es la red (un bug, una
 *   caja ajena, un 500): queda visible y se reintenta en cada vuelta.
 */
export interface EntradaCola {
  claveIdempotencia: string;
  /** Contador local que solo sube: la cola se envía en este orden, nunca por la hora del dispositivo. */
  orden: number;
  usuarioId: number;
  usuarioNombre: string;
  boticaId: number;
  cajaId: number;
  horaDispositivo: string;
  sinConexion: boolean;
  estado: 'PENDIENTE' | 'DETENIDA';
  ultimoError: string | null;
  /** El cuerpo tal cual (con precioCobrado por línea); sinConexion se agrega al enviarlo desde la cola. */
  cuerpo: Omit<NuevaVentaRequest, 'sinConexion'>;
  /** Para el panel "Ventas sin enviar": sin montos (conteo ciego). */
  productos: string[];
  /** Unidades base por producto: el stock aproximado local descuenta lo que está en cola. */
  unidadesPorProducto: Record<number, number>;
}

/** Tarea 22: una venta de la cola que el servidor aceptó con observaciones (solo en memoria de la sesión). */
export interface EnviadaConObservaciones {
  claveIdempotencia: string;
  ventaId: number;
  productos: string[];
  revisiones: RevisionVenta[];
}
