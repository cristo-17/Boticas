import { RangoRapido } from '../../core/utils/fecha-lima.util';

export interface AccesoRapido {
  id: RangoRapido;
  label: string;
}

/** Accesos rápidos de fecha de Reportes (Resultados y DIGEMID). */
export const ACCESOS_RAPIDOS: AccesoRapido[] = [
  { id: 'hoy', label: 'Hoy' },
  { id: 'semana', label: 'Esta semana' },
  { id: 'mes', label: 'Este mes' },
  { id: 'mes-anterior', label: 'Mes anterior' },
  { id: 'ultimos-30', label: 'Últimos 30 días' },
  { id: 'personalizado', label: 'Personalizado' },
];

/** YYYY-MM-DD → dd/MM/yyyy (solo presentación). */
export function fechaCorta(iso: string | null): string {
  if (!iso) return '';
  const [y, m, d] = iso.split('-');
  return y && m && d ? `${d}/${m}/${y}` : iso;
}
