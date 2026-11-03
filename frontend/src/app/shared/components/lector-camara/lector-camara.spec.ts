import { TestBed } from '@angular/core/testing';
import { CREAR_DETECTOR, LectorCamaraComponent, clasificarFallaCamara } from './lector-camara';
import { Detector } from './motor-lectura';
import { RELOJ } from '../../../core/utils/lector-usb';

/**
 * Tarea 19. jsdom no tiene cámara: getUserMedia y el detector son falsos.
 * Lo que importa probar acá es que TODAS las pistas se detienen (la luz se
 * apaga) en cada salida, y qué hace el lector con lo que ve. En el celular
 * se confirma a mano (PLAN-DE-PRUEBAS, B5).
 */
function pistaFalsa() {
  return { stop: vi.fn() };
}

function streamFalso() {
  const pistas = [pistaFalsa(), pistaFalsa()]; // video + otra: se detienen todas, no solo la primera
  return { pistas, stream: { getTracks: () => pistas } as unknown as MediaStream };
}

const DETECTOR: Detector = { detect: vi.fn().mockResolvedValue([]) };

describe('LectorCamaraComponent', () => {
  let getUserMedia: ReturnType<typeof vi.fn>;
  let crear: ReturnType<typeof vi.fn>;
  let ahora: number;
  const vibrate = vi.fn();

  beforeEach(() => {
    ahora = 0;
    getUserMedia = vi.fn();
    crear = vi.fn().mockResolvedValue({ detector: DETECTOR, motor: 'zxing' });
    Object.defineProperty(navigator, 'mediaDevices', {
      value: { getUserMedia },
      configurable: true,
    });
    Object.defineProperty(navigator, 'vibrate', { value: vibrate, configurable: true });
    vibrate.mockClear();
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
    TestBed.configureTestingModule({
      imports: [LectorCamaraComponent],
      providers: [
        { provide: CREAR_DETECTOR, useValue: crear },
        { provide: RELOJ, useValue: () => ahora },
      ],
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
  });

  function crearLector() {
    const fixture = TestBed.createComponent(LectorCamaraComponent);
    fixture.detectChanges();
    return fixture;
  }

  async function abierto() {
    const { pistas, stream } = streamFalso();
    getUserMedia.mockResolvedValue(stream);
    const fixture = crearLector();
    await fixture.componentInstance.abrir();
    fixture.detectChanges();
    return { fixture, lector: fixture.componentInstance, pistas };
  }

  it('abrir: pide la cámara trasera sin audio y queda activa con el motor cargado', async () => {
    const { lector } = await abierto();
    const restricciones = getUserMedia.mock.calls[0][0] as MediaStreamConstraints;
    expect(restricciones.audio).toBe(false);
    expect((restricciones.video as MediaTrackConstraints).facingMode).toEqual({
      ideal: 'environment',
    });
    expect(lector.camara()).toBe('activa');
    expect(lector.motor()).toBe('zxing');
    lector.cerrar();
  });

  it('cerrar: detiene TODAS las pistas y el video suelta el stream', async () => {
    const { fixture, lector, pistas } = await abierto();
    lector.cerrar();
    expect(pistas.every((p) => p.stop.mock.calls.length === 1)).toBe(true);
    expect((fixture.nativeElement.querySelector('video') as HTMLVideoElement).srcObject).toBeNull();
    expect(lector.camara()).toBe('apagada');
  });

  it('destruir el componente (salir de la pantalla o cerrar el modal) detiene todas las pistas', async () => {
    const { fixture, pistas } = await abierto();
    fixture.destroy();
    expect(pistas.every((p) => p.stop.mock.calls.length === 1)).toBe(true);
  });

  it('la página pasa a segundo plano: detiene las pistas y NO se reabre sola al volver', async () => {
    const { lector, pistas } = await abierto();
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
    document.dispatchEvent(new Event('visibilitychange'));
    expect(pistas.every((p) => p.stop.mock.calls.length === 1)).toBe(true);

    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
    document.dispatchEvent(new Event('visibilitychange'));
    expect(lector.camara()).toBe('apagada');
    expect(getUserMedia).toHaveBeenCalledTimes(1);
  });

  it('pagehide detiene las pistas', async () => {
    const { pistas } = await abierto();
    window.dispatchEvent(new Event('pagehide'));
    expect(pistas.every((p) => p.stop.mock.calls.length === 1)).toBe(true);
  });

  it('cerrar mientras se pide el permiso: cuando el permiso llega tarde, sus pistas se detienen igual', async () => {
    const { pistas, stream } = streamFalso();
    let conceder!: (s: MediaStream) => void;
    getUserMedia.mockReturnValue(new Promise<MediaStream>((r) => (conceder = r)));
    const lector = crearLector().componentInstance;
    const apertura = lector.abrir();
    lector.cerrar();
    conceder(stream);
    await apertura;
    expect(pistas.every((p) => p.stop.mock.calls.length === 1)).toBe(true);
    expect(lector.camara()).toBe('apagada');
  });

  it('permiso denegado: mensaje de permiso y la cámara queda apagada', async () => {
    getUserMedia.mockRejectedValue(new DOMException('denegado', 'NotAllowedError'));
    const fixture = crearLector();
    await fixture.componentInstance.abrir();
    fixture.detectChanges();
    expect(fixture.componentInstance.falla()).toBe('permiso');
    expect(fixture.nativeElement.textContent).toContain('Sin permiso para usar la cámara');
    expect(fixture.nativeElement.textContent).toContain('Abrir cámara');
  });

  it('sin mediaDevices (sitio sin HTTPS): mensaje de conexión segura', async () => {
    Object.defineProperty(navigator, 'mediaDevices', { value: undefined, configurable: true });
    const lector = crearLector().componentInstance;
    await lector.abrir();
    expect(lector.falla()).toBe('inseguro');
  });

  it('el motor no carga (sin red para el .wasm): apaga la cámara que ya había abierto', async () => {
    crear.mockRejectedValue(new Error('wasm'));
    const { pistas, stream } = streamFalso();
    getUserMedia.mockResolvedValue(stream);
    const lector = crearLector().componentInstance;
    await lector.abrir();
    expect(lector.falla()).toBe('motor');
    expect(pistas.every((p) => p.stop.mock.calls.length === 1)).toBe(true);
  });

  it('clasificarFallaCamara: cada DOMException con su mensaje', () => {
    expect(clasificarFallaCamara(new DOMException('', 'NotAllowedError'))).toBe('permiso');
    expect(clasificarFallaCamara(new DOMException('', 'NotFoundError'))).toBe('sin-camara');
    expect(clasificarFallaCamara(new DOMException('', 'OverconstrainedError'))).toBe('sin-camara');
    expect(clasificarFallaCamara(new DOMException('', 'NotReadableError'))).toBe('en-uso');
  });

  describe('procesar (lo que el detector vio en un cuadro)', () => {
    it('un EAN-13 válido sale una vez, con vibración corta', async () => {
      const { lector } = await abierto();
      const leidos: string[] = [];
      lector.leido.subscribe((c) => leidos.push(c));
      lector.procesar([{ rawValue: '7751234000115', format: 'ean_13' }]);
      expect(leidos).toEqual(['7751234000115']);
      expect(vibrate).toHaveBeenCalledWith(60);
      lector.cerrar();
    });

    it('dígito verificador inválido u otro formato: descartado en silencio, sin vibrar', async () => {
      const { lector } = await abierto();
      const leidos: string[] = [];
      lector.leido.subscribe((c) => leidos.push(c));
      lector.procesar([{ rawValue: '7751234000118', format: 'ean_13' }]);
      lector.procesar([{ rawValue: '7751234000115', format: 'qr_code' }]);
      expect(leidos).toEqual([]);
      expect(vibrate).not.toHaveBeenCalled();
      lector.cerrar();
    });

    it('el mismo código cuadro tras cuadro sale una vez; tras 1,5 s sin verlo, otra vez', async () => {
      const { lector } = await abierto();
      const leidos: string[] = [];
      lector.leido.subscribe((c) => leidos.push(c));
      for (ahora = 0; ahora <= 3000; ahora += 150) {
        lector.procesar([{ rawValue: '7751234000115', format: 'ean_13' }]);
      }
      ahora += 1600;
      lector.procesar([{ rawValue: '7751234000115', format: 'ean_13' }]);
      expect(leidos).toEqual(['7751234000115', '7751234000115']);
      lector.cerrar();
    });

    it('pausado (modal abierto): no emite, y el código que sigue a la vista no sale al reanudar', async () => {
      const { fixture, lector } = await abierto();
      const leidos: string[] = [];
      lector.leido.subscribe((c) => leidos.push(c));
      fixture.componentRef.setInput('pausado', true);
      lector.procesar([{ rawValue: '7751234000115', format: 'ean_13' }]);
      ahora = 150;
      fixture.componentRef.setInput('pausado', false);
      lector.procesar([{ rawValue: '7751234000115', format: 'ean_13' }]);
      expect(leidos).toEqual([]);
      lector.cerrar();
    });
  });
});
