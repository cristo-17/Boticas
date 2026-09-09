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

import type { EstadoVencimiento } from '../models/estados.model';

/**
 * Texto de alerta ("Vence en N días" / "Vencido hace N días") a partir
 * del estado y la fecha que manda el servidor — solo si el estado
 * amerita aviso (CRITICO/VENCIDO). El servidor manda el estado (regla
 * de negocio) y la fecha (dato); el conteo de días se hace acá, nunca
 * al revés (docs/API-CONTRATO.md) — evita mandar un texto que caduca.
 */
export function textoAlertaVencimiento(
  estado: EstadoVencimiento | null | undefined,
  fechaVencimiento: string | null | undefined,
): string | null {
  if (!fechaVencimiento || (estado !== 'CRITICO' && estado !== 'VENCIDO')) {
    return null;
  }
  const dias = diasHasta(fechaVencimiento);
  return dias < 0 ? `Vencido hace ${Math.abs(dias)} días` : `Vence en ${dias} días`;
}
