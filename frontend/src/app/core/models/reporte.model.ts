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
