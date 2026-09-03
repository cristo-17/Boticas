import { Turno } from './usuario.model';

export interface CajaDiaria {
  id: string;
  fecha: string; // ISO date
  usuarioId: string;
  usuarioNombre: string;
  turno: Turno;
  montoApertura: number;
  horaApertura: string; // ISO datetime
  horaCierre: string | null;
  montoContado: number | null;
  diferencia: number | null;
  observaciones: string | null;
  abierta: boolean;
}

export type TipoMovimientoCaja = 'apertura' | 'venta' | 'ingreso' | 'egreso' | 'merma';

export interface MovimientoCaja {
  id: string;
  cajaId: string;
  tipo: TipoMovimientoCaja;
  descripcion: string;
  nota: string;
  monto: number;
  /** false en movimientos que no mueven efectivo del cajón (p.ej. mermas, ventas con Yape/tarjeta). */
  afectaEfectivo: boolean;
}

export interface AbrirCajaRequest {
  montoInicial: number;
  turno: Turno;
}

export interface CerrarCajaRequest {
  montoContado: number;
  observaciones?: string;
}
