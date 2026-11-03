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

/**
 * Datos de la receta (D-E2-4, Tarea 20): número y CMP del médico obligatorios
 * (CMP: 1 a 6 dígitos), nombre del médico y fecha de emisión opcionales. La
 * fecha no puede ser futura (400 RECETA_INVALIDA).
 */
export interface RecetaVenta {
  numero: string;
  cmp: string;
  medicoNombre: string | null;
  fechaEmision: string | null; // yyyy-MM-dd
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
  /** Tarea 20: la receta guardada con la venta; null si ninguna línea la necesitó. */
  receta: RecetaVenta | null;
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
  /** Tarea 20: obligatoria si alguna línea es de un producto con requiereReceta; si ninguna lo es, el servidor la ignora. */
  receta?: RecetaVenta | null;
}
