import { fechaLimaIso, lunesDeLaSemana, rangoRapido, sumarDias } from './fecha-lima.util';

describe('fecha-lima.util (Tarea 18, Bloque C)', () => {
  // Domingo 2026-11-08 a las 23:30 en Lima = lunes 2026-11-09T04:30Z.
  const domingo2330Lima = new Date('2026-11-09T04:30:00Z');
  // Martes 2026-03-03 10:00 en Lima.
  const martesMarzo = new Date('2026-03-03T15:00:00Z');

  it('fechaLimaIso usa el calendario de Lima, no el UTC ni el del navegador', () => {
    expect(fechaLimaIso(domingo2330Lima)).toBe('2026-11-08');
  });

  it('sumarDias cruza meses y años bisiestos', () => {
    expect(sumarDias('2026-03-01', -1)).toBe('2026-02-28');
    expect(sumarDias('2028-03-01', -1)).toBe('2028-02-29');
    expect(sumarDias('2026-12-31', 1)).toBe('2027-01-01');
  });

  it('lunesDeLaSemana: semana de lunes a domingo', () => {
    expect(lunesDeLaSemana('2026-11-02')).toBe('2026-11-02'); // lunes
    expect(lunesDeLaSemana('2026-11-05')).toBe('2026-11-02'); // jueves
    expect(lunesDeLaSemana('2026-11-08')).toBe('2026-11-02'); // domingo
  });

  it('accesos rápidos a las 23:30 del domingo en Lima (UTC ya es lunes)', () => {
    expect(rangoRapido('hoy', domingo2330Lima)).toEqual({ desde: '2026-11-08', hasta: '2026-11-08' });
    expect(rangoRapido('semana', domingo2330Lima)).toEqual({ desde: '2026-11-02', hasta: '2026-11-08' });
    expect(rangoRapido('mes', domingo2330Lima)).toEqual({ desde: '2026-11-01', hasta: '2026-11-08' });
    expect(rangoRapido('mes-anterior', domingo2330Lima)).toEqual({ desde: '2026-10-01', hasta: '2026-10-31' });
    expect(rangoRapido('ultimos-30', domingo2330Lima)).toEqual({ desde: '2026-10-10', hasta: '2026-11-08' });
  });

  it('mes anterior en marzo termina el 28 de febrero', () => {
    expect(rangoRapido('mes-anterior', martesMarzo)).toEqual({ desde: '2026-02-01', hasta: '2026-02-28' });
  });
});
