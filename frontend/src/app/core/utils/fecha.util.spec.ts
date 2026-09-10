import { diasHasta, textoAlertaVencimiento } from './fecha.util';

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

  it('da null si no hay fecha, aunque el estado sea CRITICO', () => {
    expect(textoAlertaVencimiento('CRITICO', null)).toBeNull();
  });

  it('para VENCIDO redacta "Vencido hace N días"', () => {
    const ayer = new Date();
    ayer.setDate(ayer.getDate() - 3);
    const iso = ayer.toISOString().slice(0, 10);
    expect(textoAlertaVencimiento('VENCIDO', iso)).toBe('Vencido hace 3 días');
  });

  it('para CRITICO redacta "Vence en N días"', () => {
    const enUnaSemana = new Date();
    enUnaSemana.setDate(enUnaSemana.getDate() + 7);
    const iso = enUnaSemana.toISOString().slice(0, 10);
    expect(textoAlertaVencimiento('CRITICO', iso)).toBe('Vence en 7 días');
  });
});
