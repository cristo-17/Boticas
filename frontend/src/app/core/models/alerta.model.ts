export type NivelAlerta = 'urgente' | 'atencion' | 'informativa';

export interface Alerta {
  id: string;
  nivel: NivelAlerta;
  titulo: string;
  cuerpo: string;
  monto: string | null;
  accionLabel: string;
  accionRuta: string;
  /* Query params con filtro aplicado */
  accionQueryParams?: Record<string, string>;
}

/* KPIs del dashboard de alertas */
export interface ResumenDashboard {
  ventasHoyTexto: string;
  ventasHoyNota: string;
  productosPorVencer: number;
  productosPorVencerNota: string;
  stockCritico: number;
  stockCriticoNota: string;
  cajaEstado: 'Abierta' | 'Cerrada';
  cajaNota: string;
}
