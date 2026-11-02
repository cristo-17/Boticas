export type AgrupacionReporte = 'dia' | 'mes';

export interface FilaResultado {
  periodo: string;
  ventas: number;
  costoVentas: number;
  margenBruto: number;
  mermasCosto: number;
  egresos: number;
  resultado: number;
}

export interface TotalesResultado {
  ventas: number;
  costoVentas: number;
  margenBruto: number;
  mermasCosto: number;
  egresos: number;
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
