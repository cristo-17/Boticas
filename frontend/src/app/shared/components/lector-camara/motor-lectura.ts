/**
 * Tarea 19 (Bloque A aprobado): `BarcodeDetector` nativo si el navegador
 * lo trae y soporta `ean_13` (Chrome de Android); si no (Safari de iPhone,
 * Chrome de Windows), `barcode-detector/ponyfill` con ZXing-C++ en WASM.
 * Los dos exponen la misma interfaz, así que el resto del lector no sabe
 * cuál está usando.
 *
 * El ponyfill entra por import dinámico: es un chunk aparte que solo se
 * descarga al abrir la cámara en un navegador sin lector nativo. El .wasm
 * se sirve desde nuestro dominio (`/zxing/zxing_reader.wasm`, copiado por
 * angular.json desde node_modules), no desde el CDN que la librería usa
 * por defecto: así funciona sin conexión (ngsw-config, grupo lazy) y no
 * depende de un tercero.
 */
export const FORMATOS_EAN = ['ean_13', 'ean_8'] as const;

export type MotorLectura = 'nativo' | 'zxing';

export interface CodigoDetectado {
  readonly rawValue: string;
  readonly format: string;
}

export interface Detector {
  detect(fuente: HTMLVideoElement): Promise<readonly CodigoDetectado[]>;
}

/** Ruta pública del .wasm; tiene que coincidir con el `output` del asset en angular.json. */
export const RUTA_WASM = 'zxing/';

interface BarcodeDetectorNativo {
  new (opciones: { formats: string[] }): Detector;
  getSupportedFormats(): Promise<string[]>;
}

/** `?lector=zxing` en la URL fuerza el respaldo en un Android, para probarlo en el celular (PLAN-DE-PRUEBAS). */
function forzarZxing(): boolean {
  return new URLSearchParams(globalThis.location?.search ?? '').get('lector') === 'zxing';
}

export async function crearDetector(): Promise<{ detector: Detector; motor: MotorLectura }> {
  const Nativo = (globalThis as { BarcodeDetector?: BarcodeDetectorNativo }).BarcodeDetector;
  if (Nativo && !forzarZxing()) {
    try {
      const soportados = await Nativo.getSupportedFormats();
      if (soportados.includes('ean_13')) {
        const formatos = FORMATOS_EAN.filter((f) => soportados.includes(f));
        return { detector: new Nativo({ formats: formatos }), motor: 'nativo' };
      }
    } catch {
      // Sin servicio de detección en el sistema (p. ej. Android sin Play Services): respaldo.
    }
  }

  const { BarcodeDetector, prepareZXingModule } = await import('barcode-detector/ponyfill');
  const base = new URL(RUTA_WASM, document.baseURI).href;
  // fireImmediately: si el .wasm no llega (sin red, ruta mal servida), el error sale al abrir la cámara, no en cada cuadro.
  await prepareZXingModule({
    overrides: {
      locateFile: (ruta: string, prefijo: string) =>
        ruta.endsWith('.wasm') ? base + ruta : prefijo + ruta,
    },
    fireImmediately: true,
  });
  return { detector: new BarcodeDetector({ formats: [...FORMATOS_EAN] }), motor: 'zxing' };
}
