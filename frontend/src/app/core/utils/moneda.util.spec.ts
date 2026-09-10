import { formatearMoneda } from './moneda.util';

describe('formatearMoneda', () => {
  it('antepone "S/ " y fija 2 decimales', () => {
    expect(formatearMoneda(19.5)).toBe('S/ 19.50');
  });

  it('redondea un tercer decimal en vez de truncarlo', () => {
    expect(formatearMoneda(19.999)).toBe('S/ 20.00');
  });

  it('funciona con cero', () => {
    expect(formatearMoneda(0)).toBe('S/ 0.00');
  });

  it('no corrompe un valor que ya rompe el punto flotante (0.1 + 0.2)', () => {
    expect(formatearMoneda(0.1 + 0.2)).toBe('S/ 0.30');
  });
});
