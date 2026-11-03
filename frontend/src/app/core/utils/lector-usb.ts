import { InjectionToken } from '@angular/core';
import { esEanValido } from './ean.util';

/**
 * DIF-05 (DECISIONES 2026-11-03, "D4 revisada"): un lector de código de
 * barras USB "teclea" el código muy rápido y termina con Enter. Esta clase
 * mira la ráfaga de teclas del buscador y, al llegar el Enter, devuelve el
 * código solo si TODO se cumple:
 * - 8 o 13 teclas de dígito, sin `repeat` ni composición (IME);
 * - cada intervalo entre teclas, incluido el del Enter, ≤ INTERVALO_MAXIMO_LECTOR_MS;
 * - dígito verificador EAN válido;
 * - ningún cambio del campo que no sea una de esas teclas (pegar,
 *   autocompletar, sugerencia del teclado del celular) → `cambioAjeno()`.
 * Si falla cualquiera devuelve null y el buscador sigue la Nota 2 sin
 * cambios. Ante la duda no se marca ESCANEO: un lector no reconocido queda
 * MANUAL, nunca un tecleo humano ESCANEO.
 *
 * Es pura: el tiempo entra como parámetro (el componente lo toma de
 * `RELOJ`), así los tests no dependen del reloj real.
 */
export const INTERVALO_MAXIMO_LECTOR_MS = 50;

/** Lo que el detector necesita de un `KeyboardEvent` (un `KeyboardEvent` real lo cumple). */
export interface TeclaLector {
  readonly key: string;
  readonly repeat: boolean;
  readonly isComposing: boolean;
}

export class DetectorLectorUsb {
  private digitos = '';
  private ultimaTecla: number | null = null;

  constructor(private readonly intervaloMaximoMs = INTERVALO_MAXIMO_LECTOR_MS) {}

  /** Cada `keydown` del campo, con su instante en ms. Devuelve el código leído solo en el Enter de una ráfaga de lector. */
  tecla(evento: TeclaLector, t: number): string | null {
    const rapida = this.ultimaTecla !== null && t - this.ultimaTecla <= this.intervaloMaximoMs;

    if (evento.key === 'Enter') {
      const codigo = rapida && !evento.repeat && esEanValido(this.digitos) ? this.digitos : null;
      this.reiniciar();
      return codigo;
    }

    if (evento.repeat || evento.isComposing || !/^\d$/.test(evento.key)) {
      // Tecla mantenida, teclado del celular (key 'Unidentified', keyCode 229),
      // letras, Backspace o modificadores: ningún lector EAN los manda en medio.
      this.reiniciar();
      return null;
    }

    if (!rapida) {
      this.digitos = ''; // una pausa larga empieza una ráfaga nueva
    }
    this.digitos += evento.key;
    this.ultimaTecla = t;
    return null;
  }

  /** El campo cambió por algo que no fue una tecla de esta ráfaga (`inputType` distinto de `insertText`). */
  cambioAjeno(): void {
    this.reiniciar();
  }

  reiniciar(): void {
    this.digitos = '';
    this.ultimaTecla = null;
  }
}

/** Instante en ms para medir intervalos. Los tests lo reemplazan por un contador que avanzan a mano. */
export const RELOJ = new InjectionToken<() => number>('RELOJ', {
  providedIn: 'root',
  factory: () => () => performance.now(),
});
