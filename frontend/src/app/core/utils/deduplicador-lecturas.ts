/**
 * Tarea 19: la cámara ve el mismo código en muchos cuadros seguidos. Se
 * acepta una vez; mientras siga viéndose (sin una pausa de `ventanaMs` sin
 * verlo) no se vuelve a aceptar. Otro código se acepta enseguida.
 * Cada avistamiento, aceptado o no, renueva la ventana: mantener el código
 * frente a la cámara 5 s da una sola lectura. Puro: el tiempo entra como
 * parámetro.
 */
export const PAUSA_MISMO_CODIGO_MS = 1500;

export class DeduplicadorLecturas {
  private ultimoCodigo: string | null = null;
  private ultimoVisto = 0;

  constructor(private readonly ventanaMs = PAUSA_MISMO_CODIGO_MS) {}

  aceptar(codigo: string, t: number): boolean {
    const repetido = codigo === this.ultimoCodigo && t - this.ultimoVisto < this.ventanaMs;
    this.ultimoCodigo = codigo;
    this.ultimoVisto = t;
    return !repetido;
  }

  reiniciar(): void {
    this.ultimoCodigo = null;
    this.ultimoVisto = 0;
  }
}
