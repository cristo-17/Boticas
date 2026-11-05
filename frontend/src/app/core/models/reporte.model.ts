import { PaginaResponse } from './pagina.model';

/** Parámetro `agrupar` de GET /api/reportes/resultados[/xlsx] (semana = lunes a domingo, Lima). */
export type AgrupacionReporte = 'dia' | 'semana' | 'mes';

export interface FilaResultado {
  /** Clave estable: YYYY-MM-DD (día), YYYY-MM (mes) o primer día real de la semana en el rango. */
  periodo: string;
  /** Texto armado por el servidor: dd/MM/yyyy, MM/yyyy o "dd/MM al dd/MM". */
  etiqueta: string;
  ventas: number;
  costoVentas: number;
  margenBruto: number;
  mermasCosto: number;
  egresos: number;
  /** costoVentas + mermasCosto + egresos, calculado en el servidor (R19). */
  gastos: number;
  resultado: number;
}

export interface TotalesResultado {
  ventas: number;
  costoVentas: number;
  margenBruto: number;
  mermasCosto: number;
  egresos: number;
  gastos: number;
  resultado: number;
}

export interface ReporteResultados {
  desde: string;
  hasta: string;
  agrupar: AgrupacionReporte;
  nota: string;
  filas: FilaResultado[];
  totales: TotalesResultado;
}

/** Una línea dispensada con receta (RF-54, GET /api/reportes/digemid). */
export interface FilaDigemid {
  id: number;
  /** ISO con zona; se muestra en hora de Lima. */
  fecha: string;
  ventaId: number;
  productoId: number;
  producto: string;
  presentacion: string;
  /** Unidades base del producto (unidadNombre). */
  cantidad: number;
  unidadNombre: string;
  /** null en una línea sin lote (venta sin conexión, en revisión). */
  loteId: number | null;
  loteCodigo: string | null;
  loteVencimiento: string | null;
  /** null solo si la venta sin conexión llegó sin receta. */
  recetaNumero: string | null;
  recetaCmp: string | null;
  medicoNombre: string | null;
  fechaEmision: string | null;
  tecnico: string;
}

export interface TotalDigemidProducto {
  productoId: number;
  producto: string;
  unidadNombre: string;
  recetas: number;
  unidades: number;
}

export interface ReporteDigemid {
  desde: string;
  hasta: string;
  nota: string;
  lineas: PaginaResponse<FilaDigemid>;
  totalesPorProducto: TotalDigemidProducto[];
  totales: { lineas: number; recetas: number; unidades: number };
}
