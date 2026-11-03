import { DetectorLectorUsb, INTERVALO_MAXIMO_LECTOR_MS, TeclaLector } from './lector-usb';

/**
 * DIF-05: tiempos inventados, sin reloj real ni vi.useFakeTimers(). Cada
 * caso describe una ráfaga como teclas con su instante en ms.
 */
const EAN13 = '7751234000115';
const EAN8 = '96385074';

function tecla(key: string, extra: Partial<TeclaLector> = {}): TeclaLector {
  return { key, repeat: false, isComposing: false, ...extra };
}

/** Teclea `texto` desde `inicio` con `paso` ms entre teclas y devuelve el instante de la última. */
function teclear(d: DetectorLectorUsb, texto: string, inicio: number, paso: number): number {
  let t = inicio;
  for (const c of texto) {
    expect(d.tecla(tecla(c), t)).toBeNull(); // nada se resuelve antes del Enter
    t += paso;
  }
  return t - paso;
}

function enter(d: DetectorLectorUsb, t: number): string | null {
  return d.tecla(tecla('Enter'), t);
}

describe('DetectorLectorUsb', () => {
  let d: DetectorLectorUsb;
  beforeEach(() => (d = new DetectorLectorUsb()));

  it('el umbral aprobado es 50 ms', () => {
    expect(INTERVALO_MAXIMO_LECTOR_MS).toBe(50);
  });

  it('lector: 13 dígitos a 5 ms y Enter → devuelve el EAN-13', () => {
    const fin = teclear(d, EAN13, 1000, 5);
    expect(enter(d, fin + 5)).toBe(EAN13);
  });

  it('lector: EAN-8 a 5 ms → devuelve el EAN-8', () => {
    const fin = teclear(d, EAN8, 0, 5);
    expect(enter(d, fin + 5)).toBe(EAN8);
  });

  it('justo en el umbral (50 ms entre todas las teclas) todavía es lector; a 51 ms ya no', () => {
    expect(enter(d, teclear(d, EAN13, 0, 50) + 50)).toBe(EAN13);
    expect(enter(d, teclear(d, EAN13, 10_000, 51) + 51)).toBeNull();
  });

  it('persona: los mismos 13 dígitos a 120 ms → null (sigue la Nota 2)', () => {
    const fin = teclear(d, EAN13, 0, 120);
    expect(enter(d, fin + 120)).toBeNull();
  });

  it('una sola pausa de 80 ms en medio corta la ráfaga → null', () => {
    let t = teclear(d, EAN13.slice(0, 6), 0, 5);
    t = teclear(d, EAN13.slice(6), t + 80, 5);
    expect(enter(d, t + 5)).toBeNull();
  });

  it('una pausa de 200 ms antes del Enter → null', () => {
    const fin = teclear(d, EAN13, 0, 5);
    expect(enter(d, fin + 200)).toBeNull();
  });

  it('rápido pero con dígito verificador inválido (código del seed de desarrollo) → null', () => {
    const fin = teclear(d, '7751234000118', 0, 5);
    expect(enter(d, fin + 5)).toBeNull();
  });

  it('12 o 14 dígitos rápidos → null', () => {
    expect(enter(d, teclear(d, EAN13.slice(0, 12), 0, 5) + 5)).toBeNull();
    expect(enter(d, teclear(d, EAN13 + '0', 1000, 5) + 5)).toBeNull();
  });

  it('una tecla mantenida (repeat, cada ~33 ms) no es lector, aunque arme un EAN válido', () => {
    // "0000000000000" pasa el dígito verificador: lo único que lo frena es `repeat`.
    let t = 0;
    d.tecla(tecla('0'), t);
    for (let i = 0; i < 12; i++) {
      t += 33;
      d.tecla(tecla('0', { repeat: true }), t);
    }
    expect(enter(d, t + 33)).toBeNull();
  });

  it('teclado del celular (key "Unidentified" / composición) en medio anula la ráfaga', () => {
    let t = teclear(d, EAN13.slice(0, 5), 0, 5);
    d.tecla(tecla('Unidentified'), t + 5);
    t = teclear(d, EAN13.slice(5), t + 10, 5);
    expect(enter(d, t + 5)).toBeNull();

    const d2 = new DetectorLectorUsb();
    let t2 = teclear(d2, EAN13.slice(0, 5), 0, 5);
    d2.tecla(tecla('6', { isComposing: true }), t2 + 5);
    t2 = teclear(d2, EAN13.slice(5), t2 + 10, 5);
    expect(enter(d2, t2 + 5)).toBeNull();
  });

  it('pegar (cambio ajeno al teclado) en medio anula la ráfaga', () => {
    const fin = teclear(d, EAN13, 0, 5);
    d.cambioAjeno();
    expect(enter(d, fin + 5)).toBeNull();
  });

  it('pegar el código y apretar Enter (sin teclas) → null', () => {
    d.cambioAjeno();
    expect(enter(d, 100)).toBeNull();
  });

  it('texto tecleado antes ("par" despacio) y después el lector: cuenta solo la ráfaga', () => {
    teclear(d, 'par', 0, 150);
    const fin = teclear(d, EAN13, 2000, 5);
    expect(enter(d, fin + 5)).toBe(EAN13);
  });

  it('una letra en medio de la ráfaga la anula', () => {
    let t = teclear(d, EAN13.slice(0, 6), 0, 5);
    d.tecla(tecla('a'), t + 5);
    t = teclear(d, EAN13.slice(6), t + 10, 5);
    expect(enter(d, t + 5)).toBeNull();
  });

  it('después de un Enter empieza de cero: dos lecturas seguidas salen las dos', () => {
    expect(enter(d, teclear(d, EAN13, 0, 5) + 5)).toBe(EAN13);
    expect(enter(d, teclear(d, EAN8, 500, 5) + 5)).toBe(EAN8);
  });

  it('Enter solo (sin ráfaga) → null', () => {
    expect(enter(d, 0)).toBeNull();
  });
});
