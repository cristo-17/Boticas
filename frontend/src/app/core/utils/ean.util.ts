/**
 * Dígito verificador EAN-13 / EAN-8 (Tarea 19). Lo usan la cámara y el
 * detector de lector USB: una lectura que no lo pasa se descarta en
 * silencio, antes de buscar. Mismos vectores que el backend
 * (`test-vectores/ean.json`, leído también por `Ean13Test`).
 */

/** Verificador de un cuerpo de 7 (EAN-8) o 12 (EAN-13) dígitos: pesos 3 y 1 alternados desde la derecha. */
export function digitoVerificadorEan(cuerpo: string): number {
  let suma = 0;
  for (let i = 0; i < cuerpo.length; i++) {
    const digito = cuerpo.charCodeAt(cuerpo.length - 1 - i) - 48;
    suma += i % 2 === 0 ? digito * 3 : digito;
  }
  return (10 - (suma % 10)) % 10;
}

function esValidoDeLargo(codigo: string, largo: number): boolean {
  return (
    codigo.length === largo &&
    /^\d+$/.test(codigo) &&
    digitoVerificadorEan(codigo.slice(0, -1)) === codigo.charCodeAt(largo - 1) - 48
  );
}

export function esEan13(codigo: string): boolean {
  return esValidoDeLargo(codigo, 13);
}

export function esEan8(codigo: string): boolean {
  return esValidoDeLargo(codigo, 8);
}

/** EAN-13 o EAN-8 con verificador válido. No recorta espacios: lo que leyó la máquina se valida tal cual. */
export function esEanValido(codigo: string): boolean {
  return esEan13(codigo) || esEan8(codigo);
}
