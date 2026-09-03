export type MetodoPago = 'efectivo' | 'yape' | 'tarjeta';

export interface ItemVenta {
  productoId: string;
  presentacionId: string;
  nombre: string;
  presentacion: string; // etiqueta de la presentación vendida, p.ej. 'Blíster'
  precioUnitario: number;
  cantidad: number;
}

export interface Venta {
  id: string;
  fecha: string; // ISO datetime
  usuarioId: string;
  items: ItemVenta[];
  subtotal: number;
  igv: number;
  total: number;
  metodoPago: MetodoPago;
  sincronizada: boolean;
}

/** Cuerpo para registrar una venta nueva; el servidor calcula precios, IGV y totales. */
export interface NuevaVentaRequest {
  items: { productoId: string; presentacionId: string; cantidad: number }[];
  metodoPago: MetodoPago;
}
