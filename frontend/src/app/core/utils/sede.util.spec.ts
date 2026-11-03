import { textoSede } from './sede.util';

describe('textoSede', () => {
  it('nombre y dirección separados por " · "', () => {
    expect(textoSede('Botica San Lucas', 'Av. Grau 412, Chiclayo')).toBe(
      'Botica San Lucas · Av. Grau 412, Chiclayo',
    );
  });

  it('sin dirección (null, undefined, vacía o solo espacios) queda solo el nombre, nunca "null"', () => {
    expect(textoSede('Charito', null)).toBe('Charito');
    expect(textoSede('Charito', undefined)).toBe('Charito');
    expect(textoSede('Charito', '')).toBe('Charito');
    expect(textoSede('Charito', '   ')).toBe('Charito');
  });

  it('recorta espacios de los extremos', () => {
    expect(textoSede('  Charito ', ' Jr. Lima 1 ')).toBe('Charito · Jr. Lima 1');
  });
});
