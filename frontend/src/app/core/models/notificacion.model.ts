export type TipoNotificacion = 'DESCUADRE_GRAVE' | 'STOCK_CRITICO' | 'LOTE_VENCIDO' | 'INFO';

export interface Notificacion {
  id: number;
  tipo: TipoNotificacion;
  titulo: string;
  mensaje: string;
  leido: boolean;
  fechaCreacion: string;
}
