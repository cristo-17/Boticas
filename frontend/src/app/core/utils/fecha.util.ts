/**
 * Días entre hoy y una fecha ISO (yyyy-MM-dd). Negativo si ya pasó.
 * Se usa en inventario, merma y alertas para el semáforo FEFO — el
 * backend solo entrega fechaVencimiento; el estado se deriva aquí para
 * no duplicar la misma cuenta en cada pantalla.
 */
export function diasHasta(fechaIso: string, desde: Date = new Date()): number {
  const hoy = new Date(desde.getFullYear(), desde.getMonth(), desde.getDate());
  const [anio, mes, dia] = fechaIso.split('-').map(Number);
  const fecha = new Date(anio, mes - 1, dia);
  const msPorDia = 24 * 60 * 60 * 1000;
  return Math.round((fecha.getTime() - hoy.getTime()) / msPorDia);
}

export type EstadoFefo = 'ok' | 'pronto' | 'critico';

/** ok: más de 90 días · pronto: 30 a 90 · critico: menos de 30 o ya vencido. */
export function estadoFefo(dias: number): EstadoFefo {
  if (dias <= 90 && dias >= 30) return 'pronto';
  if (dias < 30) return 'critico';
  return 'ok';
}
