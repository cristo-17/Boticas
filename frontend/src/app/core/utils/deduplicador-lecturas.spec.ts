import { DeduplicadorLecturas, PAUSA_MISMO_CODIGO_MS } from './deduplicador-lecturas';

describe('DeduplicadorLecturas', () => {
  const A = '7751234000115';
  const B = '96385074';

  it('la pausa entre lecturas del mismo código es 1,5 s', () => {
    expect(PAUSA_MISMO_CODIGO_MS).toBe(1500);
  });

  it('el mismo código visto cuadro a cuadro durante 5 s se acepta una sola vez', () => {
    const d = new DeduplicadorLecturas();
    const aceptados = [];
    for (let t = 0; t <= 5000; t += 150) {
      if (d.aceptar(A, t)) aceptados.push(t);
    }
    expect(aceptados).toEqual([0]);
  });

  it('si se deja de ver 1,5 s y vuelve, se acepta otra vez; antes no', () => {
    const d = new DeduplicadorLecturas();
    expect(d.aceptar(A, 0)).toBe(true);
    expect(d.aceptar(A, 1499)).toBe(false);
    expect(d.aceptar(A, 1499 + 1500)).toBe(true);
  });

  it('otro código se acepta enseguida, y volver al primero también', () => {
    const d = new DeduplicadorLecturas();
    expect(d.aceptar(A, 0)).toBe(true);
    expect(d.aceptar(B, 10)).toBe(true);
    expect(d.aceptar(A, 20)).toBe(true);
  });

  it('reiniciar() olvida el último código', () => {
    const d = new DeduplicadorLecturas();
    d.aceptar(A, 0);
    d.reiniciar();
    expect(d.aceptar(A, 10)).toBe(true);
  });
});
