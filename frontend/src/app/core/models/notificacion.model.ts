/** Los únicos que genera el backend (CHECK de V6). Un tipo nuevo empieza en la migración, no acá. */
export type TipoNotificacion = 'DESCUADRE_GRAVE' | 'STOCK_CRITICO';

export interface Notificacion {
  id: number;
  tipo: TipoNotificacion;
  titulo: string;
  mensaje: string;
  leido: boolean;
  fechaCreacion: string;
}

/** POST /api/notificaciones/ticket — un solo uso, 60 s. Solo sirve para abrir /stream (H2). */
export interface TicketSse {
  ticket: string;
  expiraEnSegundos: number;
}
