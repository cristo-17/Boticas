import { diasHasta, textoAlertaVencimiento, textoHaceCuanto } from './fecha.util';

describe('diasHasta', () => {
  it('da 0 para la fecha de hoy', () => {
    const hoy = new Date(2026, 0, 15);
    expect(diasHasta('2026-01-15', hoy)).toBe(0);
  });

  it('da positivo para una fecha futura', () => {
    const hoy = new Date(2026, 0, 15);
    expect(diasHasta('2026-01-20', hoy)).toBe(5);
  });

  it('da negativo para una fecha ya vencida', () => {
    const hoy = new Date(2026, 0, 15);
    expect(diasHasta('2026-01-10', hoy)).toBe(-5);
  });

  it('cruza el fin de mes correctamente', () => {
    const hoy = new Date(2026, 0, 30); // 30 de enero
    expect(diasHasta('2026-02-02', hoy)).toBe(3);
  });
});

describe('textoAlertaVencimiento', () => {
  it('da null si el estado es OK, aunque la fecha exista', () => {
    expect(textoAlertaVencimiento('OK', '2026-01-20')).toBeNull();
  });

  it('sin estado ni fecha (ningún lote vendible, DIF-13) avisa "Sin stock vendible"', () => {
    // Desde la Tarea 0B-A el servidor manda null en los dos cuando no queda ningún lote con
    // stock y sin vencer: el cajero tiene que verlo ANTES de cobrar, no como un 422 al final.
    expect(textoAlertaVencimiento(null, null)).toBe('Sin stock vendible');
  });

  it('da null si no hay fecha, aunque el estado sea CRITICO', () => {
    expect(textoAlertaVencimiento('CRITICO', null)).toBeNull();
  });

  it('para VENCIDO redacta "Vencido hace N días"', () => {
    const ayer = new Date();
    ayer.setDate(ayer.getDate() - 3);
    const anio = ayer.getFullYear();
    const mes = String(ayer.getMonth() + 1).padStart(2, '0');
    const dia = String(ayer.getDate()).padStart(2, '0');
    const iso = `${anio}-${mes}-${dia}`;
    expect(textoAlertaVencimiento('VENCIDO', iso)).toBe('Vencido hace 3 días');
  });

  it('para CRITICO redacta "Vence en N días"', () => {
    const enUnaSemana = new Date();
    enUnaSemana.setDate(enUnaSemana.getDate() + 7);
    const anio = enUnaSemana.getFullYear();
    const mes = String(enUnaSemana.getMonth() + 1).padStart(2, '0');
    const dia = String(enUnaSemana.getDate()).padStart(2, '0');
    const iso = `${anio}-${mes}-${dia}`;
    expect(textoAlertaVencimiento('CRITICO', iso)).toBe('Vence en 7 días');
  });

  it('singular y "hoy": "Vence en 1 día", "Vencido hace 1 día", "Vence hoy" (antes "Vence en 1 días")', () => {
    const desde = new Date(2026, 10, 3);
    expect(textoAlertaVencimiento('CRITICO', '2026-11-04', desde)).toBe('Vence en 1 día');
    expect(textoAlertaVencimiento('VENCIDO', '2026-11-02', desde)).toBe('Vencido hace 1 día');
    expect(textoAlertaVencimiento('CRITICO', '2026-11-03', desde)).toBe('Vence hoy');
  });
});

describe('textoHaceCuanto', () => {
  const ahora = Date.parse('2026-11-03T21:30:00-05:00');
  it('minutos, horas y días', () => {
    expect(textoHaceCuanto('2026-11-03T21:29:40-05:00', ahora)).toBe('hace menos de 1 min');
    expect(textoHaceCuanto('2026-11-03T21:23:00-05:00', ahora)).toBe('hace 7 min');
    expect(textoHaceCuanto('2026-11-03T19:00:00-05:00', ahora)).toBe('hace 2 h');
    expect(textoHaceCuanto('2026-11-02T20:00:00-05:00', ahora)).toBe('hace 1 día');
    expect(textoHaceCuanto('2026-10-30T20:00:00-05:00', ahora)).toBe('hace 4 días');
  });
});
