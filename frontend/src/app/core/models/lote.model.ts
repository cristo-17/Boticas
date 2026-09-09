import { EstadoVencimiento, StockEstado } from './estados.model';

/** Un lote físico de un producto en stock. costoUnitario NUNCA viaja acá (D2): es información del dueño, no del cajero. */
export interface Lote {
  id: number;
  productoId: number;
  productoNombre: string;
  categoria: string;
  codigo: string; // p.ej. 'L-2405A'
  fechaVencimiento: string; // ISO 8601 (yyyy-MM-dd)
  stock: number;
  ubicacion: string;
  precioUnitario: number; // de presentaciones (factorConversion=1), no de una columna propia
  estadoVencimiento: EstadoVencimiento;
  stockEstado: StockEstado;
}

export interface FiltroLotes {
  categoria?: string;
  vencimiento?: 'todos' | 'ok' | 'advertencia' | 'critico' | 'vencido';
  soloStockBajo?: boolean;
  /** Reemplaza el antiguo filtro por productoNombre — lo necesita el flujo rediseñado de Merma (Bloque C). */
  productoId?: number;
}

export interface NuevoLoteRequest {
  productoId: number;
  codigo: string;
  fechaVencimiento: string;
  stock: number;
  ubicacion: string;
  costoUnitario: number;
}
