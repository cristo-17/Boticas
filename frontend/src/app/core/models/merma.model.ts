export type MotivoMerma = 'Vencimiento' | 'Rotura' | 'Deterioro' | 'Robo o pérdida' | 'Otro';

export interface Merma {
  id: string;
  loteId: number;
  productoNombre: string;
  loteCodigo: string;
  cantidad: number;
  motivo: MotivoMerma;
  observacion: string | null;
  valor: number;
  usuarioId: string;
  fecha: string; // ISO datetime
}

/** El motivo 'Robo o pérdida' y 'Otro' exigen observación (se valida también en el backend). */
export interface NuevaMermaRequest {
  loteId: number;
  cantidad: number;
  motivo: MotivoMerma;
  observacion?: string;
}
