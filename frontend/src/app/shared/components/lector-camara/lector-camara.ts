import {
  Component,
  DestroyRef,
  ElementRef,
  InjectionToken,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { ButtonComponent } from '../button/button';
import { esEanValido } from '../../../core/utils/ean.util';
import { DeduplicadorLecturas } from '../../../core/utils/deduplicador-lecturas';
import { RELOJ } from '../../../core/utils/lector-usb';
import {
  CodigoDetectado,
  Detector,
  FORMATOS_EAN,
  MotorLectura,
  crearDetector,
} from './motor-lectura';

/** Resultado de la búsqueda que hizo la pantalla con el código leído (la pinta el marco). */
export type EstadoEscaneo = 'idle' | 'buscando' | 'encontrado' | 'error';

export type EstadoCamara = 'apagada' | 'iniciando' | 'activa';

export type FallaCamara = 'permiso' | 'sin-camara' | 'en-uso' | 'inseguro' | 'motor';

/** Los tests lo reemplazan por un detector falso: sin import dinámico ni WASM. */
export const CREAR_DETECTOR = new InjectionToken<typeof crearDetector>('CREAR_DETECTOR', {
  providedIn: 'root',
  factory: () => crearDetector,
});

/** Pausa entre un cuadro analizado y el siguiente. ZXing tarda ~20-60 ms por cuadro en un celular medio. */
export const INTERVALO_LECTURA_MS = 150;

const RESTRICCIONES: MediaStreamConstraints = {
  audio: false,
  video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
};

const TEXTO_FALLA: Record<FallaCamara, { titulo: string; texto: string }> = {
  permiso: {
    titulo: 'Sin permiso para usar la cámara',
    texto: 'Permite la cámara para este sitio en los ajustes del navegador, o digita el código.',
  },
  'sin-camara': { titulo: 'No se encontró una cámara', texto: 'Digita el código en el campo.' },
  'en-uso': {
    titulo: 'La cámara está en uso',
    texto: 'Ciérrala en la otra aplicación y vuelve a intentar, o digita el código.',
  },
  inseguro: {
    titulo: 'La cámara necesita una conexión segura (HTTPS)',
    texto: 'Digita el código en el campo.',
  },
  motor: {
    titulo: 'No se pudo cargar el lector de códigos',
    texto: 'Revisa la conexión y vuelve a intentar, o digita el código.',
  },
};

const MENSAJE_BUSQUEDA: Record<EstadoEscaneo, string> = {
  idle: 'Apunta al código de barras',
  buscando: 'Buscando código…',
  encontrado: 'Producto encontrado',
  error: 'Ningún producto con este código',
};

/** Traduce el `DOMException` de `getUserMedia` a una falla con mensaje propio. */
export function clasificarFallaCamara(error: unknown): FallaCamara {
  const nombre = (error as { name?: string } | null)?.name;
  switch (nombre) {
    case 'NotAllowedError':
    case 'SecurityError':
      return 'permiso';
    case 'NotFoundError':
    case 'OverconstrainedError':
      return 'sin-camara';
    default:
      return 'en-uso'; // NotReadableError, AbortError: el sistema no entregó la cámara
  }
}

/**
 * Lector de códigos con la cámara (Tarea 19). Lo usan Punto de Venta,
 * el alta de lote y el alta de producto (≥2 pantallas → shared/).
 *
 * - La cámara se abre solo con el botón: el permiso se pide con un gesto
 *   del usuario, y al volver a la app no se reabre sola.
 * - Emite `leido` con un EAN-13/EAN-8 de dígito verificador válido; lo
 *   demás se descarta en silencio. El mismo código seguido sale una vez
 *   (`DeduplicadorLecturas`, 1,5 s).
 * - Mientras `pausado` (p. ej. el modal de presentación abierto) sigue
 *   mirando, pero no emite: así el código que sigue frente a la cámara
 *   no se vuelve a agregar al cerrar el modal.
 * - Detiene TODAS las pistas del MediaStream al cerrar, al destruirse
 *   (salir de la pantalla o cerrar el modal) y cuando la página pasa a
 *   segundo plano: la luz de la cámara se apaga.
 */
@Component({
  selector: 'app-lector-camara',
  imports: [ButtonComponent],
  templateUrl: './lector-camara.html',
  styleUrl: './lector-camara.scss',
})
export class LectorCamaraComponent {
  private readonly documento = inject(DOCUMENT);
  private readonly reloj = inject(RELOJ);
  private readonly crear = inject(CREAR_DETECTOR);

  readonly estado = input<EstadoEscaneo>('idle');
  readonly codigo = input<string | null>(null);
  readonly pausado = input(false);
  readonly compacto = input(false);

  readonly leido = output<string>();

  readonly camara = signal<EstadoCamara>('apagada');
  readonly falla = signal<FallaCamara | null>(null);
  readonly motor = signal<MotorLectura | null>(null);

  readonly textoFalla = computed(() => {
    const f = this.falla();
    return f ? TEXTO_FALLA[f] : null;
  });

  readonly mensaje = computed(() => {
    switch (this.camara()) {
      case 'apagada':
        return 'Cámara apagada';
      case 'iniciando':
        return 'Abriendo la cámara…';
      default:
        return MENSAJE_BUSQUEDA[this.estado()];
    }
  });

  readonly barriendo = computed(
    () => this.camara() === 'activa' && (this.estado() === 'idle' || this.estado() === 'buscando'),
  );

  /** No es .required: detener() puede correr al destruirse o antes de que exista la vista. */
  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');

  private stream: MediaStream | null = null;
  private detector: Detector | null = null;
  private temporizador: ReturnType<typeof setTimeout> | null = null;
  /** Cada apertura o cierre lo incrementa: una promesa de una apertura vieja que resuelve tarde no toca nada. */
  private intento = 0;
  private readonly dedup = new DeduplicadorLecturas();

  constructor() {
    const alOcultar = () => {
      if (this.documento.visibilityState === 'hidden') this.detener();
    };
    const alSalir = () => this.detener();
    this.documento.addEventListener('visibilitychange', alOcultar);
    this.documento.defaultView?.addEventListener('pagehide', alSalir);
    inject(DestroyRef).onDestroy(() => {
      this.documento.removeEventListener('visibilitychange', alOcultar);
      this.documento.defaultView?.removeEventListener('pagehide', alSalir);
      this.detener();
    });
  }

  async abrir(): Promise<void> {
    if (this.camara() !== 'apagada') return;
    const intento = ++this.intento;
    this.falla.set(null);
    this.camara.set('iniciando');

    const mediaDevices = this.documento.defaultView?.navigator.mediaDevices;
    if (!mediaDevices?.getUserMedia) {
      this.fallar('inseguro');
      return;
    }

    // El detector (y, sin lector nativo, el WASM) se carga mientras el usuario responde al permiso.
    const detectorListo = this.detector
      ? Promise.resolve({ ok: true as const, detector: this.detector, motor: this.motor()! })
      : this.crear().then(
          (d) => ({ ok: true as const, ...d }),
          () => ({ ok: false as const }),
        );

    let stream: MediaStream;
    try {
      stream = await mediaDevices.getUserMedia(RESTRICCIONES);
    } catch (error) {
      if (intento === this.intento) this.fallar(clasificarFallaCamara(error));
      return;
    }
    if (intento !== this.intento) {
      detenerPistas(stream); // se cerró mientras se pedía el permiso
      return;
    }
    this.stream = stream;

    const cargado = await detectorListo;
    if (intento !== this.intento) return; // detener() ya apagó el stream
    if (!cargado.ok) {
      this.detener();
      this.fallar('motor');
      return;
    }
    this.detector = cargado.detector;
    this.motor.set(cargado.motor);

    const video = this.video()!.nativeElement;
    video.muted = true;
    video.setAttribute('playsinline', ''); // iOS: sin esto el video se abre en pantalla completa
    video.srcObject = stream;
    try {
      await video.play();
    } catch {
      // autoplay rechazado: el video está muted y playsinline, el detector igual recibe cuadros cuando arranque
    }
    if (intento !== this.intento) return;

    this.dedup.reiniciar();
    this.camara.set('activa');
    this.programar(intento);
  }

  cerrar(): void {
    this.detener();
  }

  /** Apaga la cámara: TODAS las pistas, y el video suelta el stream. Idempotente. */
  detener(): void {
    this.intento++;
    if (this.temporizador !== null) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
    if (this.stream) {
      detenerPistas(this.stream);
      this.stream = null;
    }
    const video = this.video()?.nativeElement;
    if (video) video.srcObject = null;
    if (this.camara() !== 'apagada') this.camara.set('apagada');
  }

  private fallar(falla: FallaCamara): void {
    this.camara.set('apagada');
    this.falla.set(falla);
  }

  private programar(intento: number): void {
    this.temporizador = setTimeout(() => void this.leerCuadro(intento), INTERVALO_LECTURA_MS);
  }

  private async leerCuadro(intento: number): Promise<void> {
    if (intento !== this.intento || !this.detector) return;
    const video = this.video()?.nativeElement;
    if (!video) return;
    if (video.readyState >= 2) {
      try {
        const codigos = await this.detector.detect(video);
        if (intento !== this.intento) return;
        this.procesar(codigos);
      } catch {
        // un cuadro que el detector no pudo procesar: se sigue con el próximo
      }
    }
    if (intento === this.intento) this.programar(intento);
  }

  /** Público para los tests: lo que hace el lector con lo que devolvió el detector en un cuadro. */
  procesar(codigos: readonly CodigoDetectado[]): void {
    for (const c of codigos) {
      if (!(FORMATOS_EAN as readonly string[]).includes(c.format) || !esEanValido(c.rawValue)) {
        continue; // lectura dañada o de otro formato: en silencio
      }
      const nuevo = this.dedup.aceptar(c.rawValue, this.reloj());
      if (nuevo && !this.pausado()) {
        const navegador = this.documento.defaultView?.navigator;
        if (navegador && 'vibrate' in navegador) navegador.vibrate(60);
        this.leido.emit(c.rawValue);
      }
      return; // un código por cuadro
    }
  }
}

function detenerPistas(stream: MediaStream): void {
  for (const pista of stream.getTracks()) pista.stop();
}
