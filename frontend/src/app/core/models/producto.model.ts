/** Una presentación vendible de un producto (caja, blíster, unidad…). */
export interface PresentacionProducto {
  id: string;
  etiqueta: string; // 'Caja' | 'Blíster' | 'Unidad'...
  detalle: string; // p.ej. "100 tabletas · lote L-2405A"
  precio: number;
}

export interface Producto {
  id: string;
  nombre: string;
  laboratorio: string;
  categoria: string;
  codigoBarras: string;
  /* Texto de advertencia si el lote más próximo a vencer de este producto amerita aviso al vender; null si no hay riesgo. */
  alertaVencimiento: string | null;
  presentaciones: PresentacionProducto[];
}
