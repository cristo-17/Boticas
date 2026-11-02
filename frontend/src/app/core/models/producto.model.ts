import { EstadoVencimiento } from './estados.model';

/**
 * Una presentación vendible de un producto (caja, blíster, unidad…).
 * Sin campo "detalle" compuesto (hueco 5, docs/DECISIONES.md): el
 * backend nunca arma la frase, factorConversion/unidadNombre viajan
 * por separado y el frontend compone el texto ("100 tabletas").
 */
export interface PresentacionProducto {
  id: number;
  etiqueta: string; // 'Caja' | 'Blíster' | 'Unidad'...
  factorConversion: number;
  unidadNombre: string; // p.ej. 'tableta', 'cápsula', 'sobre'
  precio: number;
}

export interface Producto {
  id: number;
  nombre: string;
  laboratorio: string;
  categoria: string;
  codigoBarras: string;
  /**
   * Del PRIMER LOTE VENDIBLE (stock > 0 y sin vencer, fecha de Lima): el
   * mismo que va a consumir FEFO, así que nunca llega "VENCIDO" (revisa
   * D6, Tarea 0B). null en ambos si no queda ningún lote vendible: Punto
   * de Venta lo muestra como "Sin stock vendible". El servidor manda el
   * estado (regla de negocio) y la fecha (dato); el texto de días ("Vence
   * en 18 días") se arma en el cliente con diasHasta(), igual que Lote.
   */
  estadoVencimiento: EstadoVencimiento | null;
  fechaVencimiento: string | null; // ISO 8601 (yyyy-MM-dd)
  presentaciones: PresentacionProducto[];
}
