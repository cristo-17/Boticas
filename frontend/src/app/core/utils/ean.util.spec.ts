import vectores from '../../../../../test-vectores/ean.json';
import { digitoVerificadorEan, esEan13, esEan8, esEanValido } from './ean.util';

/** Mismos vectores que `Ean13Test` (JUnit): el verificador del frontend y el de la V12 no pueden divergir. */
describe('ean.util', () => {
  it('tiene vectores para probar', () => {
    expect(vectores.vectores.length).toBeGreaterThan(0);
  });

  for (const v of vectores.vectores) {
    it(`"${v.codigo}" (${v.nota}): EAN-13 ${v.esEan13}, EAN-8 ${v.esEan8}`, () => {
      expect(esEan13(v.codigo)).toBe(v.esEan13);
      expect(esEan8(v.codigo)).toBe(v.esEan8);
      expect(esEanValido(v.codigo)).toBe(v.esEan13 || v.esEan8);
    });
  }

  it('digitoVerificadorEan: cuerpos de 12 y de 7 dígitos', () => {
    expect(digitoVerificadorEan('400638133393')).toBe(1);
    expect(digitoVerificadorEan('9638507')).toBe(4);
  });
});
