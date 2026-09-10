export type MotivoMerma = 'Vencimiento' | 'Rotura' | 'Deterioro' | 'Robo o pérdida' | 'Otro';

export interface Merma {
  id: number;
  loteId: number;
  productoNombre: string;
  loteCodigo: string;
  cantidad: number;
  motivo: MotivoMerma;
  observacion: string | null;
  valorVenta: number;
  usuarioId: number;
  fecha: string; // ISO datetime
}

/** El motivo 'Robo o pérdida' y 'Otro' exigen observación — se valida también en el backend (nunca solo en el botón deshabilitado). */
export interface NuevaMermaRequest {
  loteId: number;
  cantidad: number;
  motivo: MotivoMerma;
  observacion?: string;
}
