/* Un lote físico de un producto en stock */
export interface Lote {
  id: string;
  productoId: string;
  productoNombre: string;
  categoria: string;
  codigo: string; // p.ej. 'L-2405A'
  fechaVencimiento: string; // ISO 8601 (yyyy-MM-dd)
  stock: number;
  ubicacion: string; // p.ej. 'A-2'
  precioUnitario: number;
}

export interface FiltroLotes {
  categoria?: string;
  vencimiento?: 'todos' | 'ok' | 'pronto' | 'critico'; // >90d | 30-90d | <30d o vencido
  soloStockBajo?: boolean;
}

export interface NuevoLoteRequest {
  productoId: string;
  codigo: string;
  fechaVencimiento: string;
  stock: number;
  ubicacion: string;
  precioUnitario: number;
}
