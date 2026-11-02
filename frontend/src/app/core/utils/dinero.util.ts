/**
 * Vista previa de dinero del carrito con la MISMA regla del servidor
 * (VentaTransaccion + util/Dinero): total de línea = cantidad × precio de
 * la presentación, redondeado; Op. gravada = total ÷ (1 + IGV), redondeada
 * HALF_UP; IGV = total − Op. gravada (se extrae, nunca se suma).
 *
 * Todo se cuenta en céntimos enteros: con `number` en soles, 0.10 + 0.20 da
 * 0.30000000000000004. Lo prueban los vectores compartidos con JUnit
 * (`test-vectores/dinero.json`, DECISIONES 2026-10-14). Es solo vista
 * previa: lo que se cobra es lo que devuelve el servidor.
 */
export interface ResumenDinero {
  total: number;
  opGravada: number;
  igv: number;
}

/** Los precios llegan con 2 decimales como mucho; el redondeo solo absorbe el error de `number` (0.07 × 100). */
function aCentimos(soles: number): number {
  return Math.round(soles * 100);
}

/** n ÷ d redondeado HALF_UP, ambos enteros no negativos (igual que BigDecimal con RoundingMode.HALF_UP). */
function dividirHalfUp(n: number, d: number): number {
  const cociente = Math.floor(n / d);
  return 2 * (n - cociente * d) >= d ? cociente + 1 : cociente;
}

function totalLineaCentimos(precio: number, cantidad: number): number {
  return aCentimos(precio) * cantidad;
}

export function totalLinea(precio: number, cantidad: number): number {
  return totalLineaCentimos(precio, cantidad) / 100;
}

export function resumirDinero(lineas: { precio: number; cantidad: number }[], tasaIgv: number): ResumenDinero {
  const total = lineas.reduce((acc, l) => acc + totalLineaCentimos(l.precio, l.cantidad), 0);
  // tasa en diezmilésimos (0.18 → 1800) para dividir solo enteros
  const tasa = Math.round(tasaIgv * 10000);
  const opGravada = dividirHalfUp(total * 10000, 10000 + tasa);
  return { total: total / 100, opGravada: opGravada / 100, igv: (total - opGravada) / 100 };
}
