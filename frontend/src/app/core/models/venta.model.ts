export type MetodoPago = 'efectivo' | 'yape' | 'tarjeta';

/** D4: de dónde salió la línea — nunca lo elige el cajero, se detecta (Nota 2). */
export type OrigenCaptura = 'ESCANEO' | 'MANUAL' | 'BUSQUEDA';

export interface ItemVenta {
  productoId: number;
  presentacionId: number;
  nombre: string;
  presentacion: string; // etiqueta de la presentación vendida, p.ej. 'Blíster'
  precioUnitario: number;
  cantidad: number;
  origenCaptura: OrigenCaptura;
}

export interface Venta {
  id: number;
  fecha: string; // ISO datetime
  usuarioId: number;
  items: ItemVenta[];
  subtotal: number;
  igv: number;
  total: number;
  metodoPago: MetodoPago;
  sincronizada: boolean;
  claveIdempotencia: string;
}

/**
 * Cuerpo para registrar una venta nueva; el servidor calcula precios,
 * IGV y totales — el cliente nunca manda montos (Regla 8).
 * claveIdempotencia: UUID generado al CONFIRMAR EL CARRITO (no acá,
 * no en cada intento HTTP) — ver VentaService.
 */
export interface NuevaVentaRequest {
  claveIdempotencia: string;
  items: { productoId: number; presentacionId: number; cantidad: number; origenCaptura: OrigenCaptura }[];
  metodoPago: MetodoPago;
}
