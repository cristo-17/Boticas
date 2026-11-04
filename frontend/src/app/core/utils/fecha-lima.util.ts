/**
 * Fechas de calendario en zona America/Lima (Tarea 18, Bloque C).
 *
 * "Hoy" sale de `Intl` con `timeZone: 'America/Lima'`, nunca de
 * `getDate()` del navegador: una PC con otra zona (o un servidor de
 * CI en UTC) a las 23:30 de Lima ya está en el día siguiente. El resto
 * es aritmética sobre fechas ISO `YYYY-MM-DD` en UTC puro (sin horas),
 * así que no la afecta ningún cambio de horario.
 */

export type RangoRapido = 'hoy' | 'semana' | 'mes' | 'mes-anterior' | 'ultimos-30' | 'personalizado';

export interface RangoFechas {
  desde: string;
  hasta: string;
}

const FORMATO_LIMA = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'America/Lima',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

const FORMATO_FECHA_HORA_LIMA = new Intl.DateTimeFormat('es-PE', {
  timeZone: 'America/Lima',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

/** Instante ISO (con offset) → "dd/MM/yyyy HH:mm" en hora de Lima. Solo presentación. */
export function fechaHoraLima(iso: string): string {
  const partes = Object.fromEntries(
    FORMATO_FECHA_HORA_LIMA.formatToParts(new Date(iso)).map((p) => [p.type, p.value]),
  );
  return `${partes['day']}/${partes['month']}/${partes['year']} ${partes['hour']}:${partes['minute']}`;
}

/** Fecha `YYYY-MM-DD` del calendario de Lima para un instante dado. */
export function fechaLimaIso(instante: Date = new Date()): string {
  const partes = FORMATO_LIMA.formatToParts(instante);
  const y = partes.find((p) => p.type === 'year')!.value;
  const m = partes.find((p) => p.type === 'month')!.value;
  const d = partes.find((p) => p.type === 'day')!.value;
  return `${y}-${m}-${d}`;
}

function aUtc(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

function aIso(fecha: Date): string {
  return fecha.toISOString().slice(0, 10);
}

export function sumarDias(iso: string, dias: number): string {
  const f = aUtc(iso);
  f.setUTCDate(f.getUTCDate() + dias);
  return aIso(f);
}

/** Lunes de la semana (lunes a domingo) que contiene `iso`. */
export function lunesDeLaSemana(iso: string): string {
  const diaSemana = aUtc(iso).getUTCDay(); // 0 = domingo
  return sumarDias(iso, diaSemana === 0 ? -6 : 1 - diaSemana);
}

/** Rango de un acceso rápido, calculado con la fecha de Lima de `ahora`. */
export function rangoRapido(rango: Exclude<RangoRapido, 'personalizado'>, ahora: Date = new Date()): RangoFechas {
  const hoy = fechaLimaIso(ahora);
  const inicioMes = `${hoy.slice(0, 8)}01`;
  switch (rango) {
    case 'hoy':
      return { desde: hoy, hasta: hoy };
    case 'semana':
      return { desde: lunesDeLaSemana(hoy), hasta: hoy };
    case 'mes':
      return { desde: inicioMes, hasta: hoy };
    case 'mes-anterior': {
      const finAnterior = sumarDias(inicioMes, -1);
      return { desde: `${finAnterior.slice(0, 8)}01`, hasta: finAnterior };
    }
    case 'ultimos-30':
      return { desde: sumarDias(hoy, -29), hasta: hoy };
  }
}

/**
 * "Martes, 3 de noviembre" (o "… de 2026" con año): fecha larga de Lima con SOLO la
 * primera letra en mayúscula. Antes era `text-transform: capitalize` en el CSS, que
 * ponía "3 De Noviembre" (mayúscula en cada palabra).
 */
export function textoFechaLarga(fecha: Date = new Date(), conAnio = false): string {
  const texto = fecha.toLocaleDateString('es-PE', {
    timeZone: 'America/Lima',
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    ...(conAnio ? { year: 'numeric' as const } : {}),
  });
  return texto.charAt(0).toLocaleUpperCase('es') + texto.slice(1);
}
