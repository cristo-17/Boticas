import { Turno } from './usuario.model';

export type SemaforoDescuadre = 'EXACTO' | 'LEVE' | 'GRAVE';

export interface CajaDiaria {
  id: number;
  fecha: string; // ISO date
  usuarioId: number;
  usuarioNombre: string;
  turno: Turno;
  montoApertura: number;
  horaApertura: string; // ISO datetime
  horaCierre: string | null;
  montoContado: number | null;
  /** null hasta el cierre — conteo ciego (hueco 2): el servidor no lo calcula ni lo expone antes. */
  montoEsperado: number | null;
  diferencia: number | null;
  semaforoDescuadre: SemaforoDescuadre | null;
  observaciones: string | null;
  abierta: boolean;
}

export type TipoMovimientoCaja = 'apertura' | 'venta' | 'ingreso' | 'egreso' | 'merma';

export interface MovimientoCaja {
  id: number;
  cajaId: number;
  tipo: TipoMovimientoCaja;
  descripcion: string;
  nota: string | null;
  monto: number;
  /** false en movimientos que no mueven efectivo del cajón (p.ej. mermas, ventas con Yape/tarjeta). */
  afectaEfectivo: boolean;
}

/** GET /api/caja/{id}/resumen-cierre — se pide ANTES de contar. Deliberadamente sin montoEsperado (conteo ciego). */
export interface ResumenCierre {
  totalVentasEfectivo: number;
  totalVentasDigital: number;
  cantidadVentas: number;
  cantidadMovimientos: number;
}

export interface AbrirCajaRequest {
  montoInicial: number;
  turno: Turno;
}

export interface CerrarCajaRequest {
  montoContado: number;
  observaciones?: string;
}
