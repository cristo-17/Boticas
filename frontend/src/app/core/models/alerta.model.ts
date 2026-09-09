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

/**
 * KPIs del dashboard de alertas — reformado (hueco 5, docs/API-CONTRATO.md):
 * números crudos, nunca frases ya armadas. El frontend arma
 * "+12% vs. ayer · 64 boletas" con moneda.util.ts/Intl.DateTimeFormat en
 * AlertasScreen.kpis, no acá. Deliberadamente SIN ningún monto esperado
 * de caja (conteo ciego, hueco 2) — cajaEstado es la caja del usuario
 * autenticado, no un agregado de la botica.
 */
export interface ResumenDashboard {
  ventasHoy: number;
  ventasHoyVariacionPct: number;
  boletasHoy: number;
  productosPorVencer: number;
  productosPorVencerCriticos: number;
  stockCritico: number;
  stockAgotado: number;
  cajaEstado: 'ABIERTA' | 'CERRADA';
  cajaHoraApertura: string | null; // ISO datetime
}
