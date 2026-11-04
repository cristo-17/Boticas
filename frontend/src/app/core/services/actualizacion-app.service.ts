import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, InjectionToken, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs/operators';
import { ColaVentasService } from './cola-ventas.service';
import { VentaEnCursoService } from './venta-en-curso.service';
import { ConexionService } from './conexion.service';

/** Cada cuánto se pregunta por una versión nueva mientras la app está abierta. */
export const INTERVALO_BUSQUEDA_VERSION_MS = 15 * 60_000;

/** Recarga la página. Es un token para poder reemplazarlo en los tests (jsdom no recarga). */
export const RECARGAR_PAGINA = new InjectionToken<() => void>('RECARGAR_PAGINA', {
  providedIn: 'root',
  factory: () => {
    const documento = inject(DOCUMENT);
    return () => documento.location.reload();
  },
});

/**
 * Actualizaciones de la PWA. Sin esto, el service worker baja la versión nueva
 * pero la sigue sirviendo la vieja hasta que se cierran todas las pestañas: en
 * el celular eso eran varios reinicios después de cada deploy de Vercel.
 *
 * - Pregunta por una versión nueva al arrancar, al volver la pestaña a
 *   visible y cada 15 minutos.
 * - Con la versión nueva ya descargada (VERSION_READY): si no hay una venta en
 *   curso (carrito vacío, sin cobro esperando) y la cola no está enviando,
 *   recarga sola; si no, deja `hayVersionNueva` en true y la app muestra
 *   "Hay una versión nueva · Actualizar". Nunca recarga en medio de una venta.
 * - Estado irrecuperable (el worker ya no tiene los archivos de esta versión):
 *   la misma regla (recarga sola solo si no interrumpe nada; si no, el aviso).
 * - Sin conexión nunca recarga sola (offline-fix, [FE-016]).
 *
 * La cola de ventas sin conexión está en IndexedDB: una recarga no la toca
 * (ver cola-ventas.service.spec.ts y e2e/flujo-sin-conexion.spec.ts).
 */
@Injectable({ providedIn: 'root' })
export class ActualizacionAppService {
  private readonly sw = inject(SwUpdate);
  private readonly documento = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly ventaEnCurso = inject(VentaEnCursoService);
  private readonly cola = inject(ColaVentasService);
  private readonly conexion = inject(ConexionService);
  private readonly recargarPagina = inject(RECARGAR_PAGINA);

  private readonly _hayVersionNueva = signal(false);
  readonly hayVersionNueva = this._hayVersionNueva.asReadonly();

  /**
   * Recargar ahora no interrumpe nada: ni una venta a medio hacer, ni un envío de la
   * cola, ni se recarga sin conexión (offline-fix: sin red, una recarga depende de que
   * el service worker tenga todo; mejor seguir con lo que ya está cargado).
   */
  readonly puedeRecargarSola = computed(
    () => !this.ventaEnCurso.enCurso() && !this.cola.enviando() && this.conexion.enLinea(),
  );

  private iniciado = false;

  /** Lo llama la raíz de la app una vez. Sin service worker (desarrollo, tests) no hace nada. */
  iniciar(): void {
    if (this.iniciado || !this.sw.isEnabled) return;
    this.iniciado = true;

    this.sw.versionUpdates
      .pipe(
        filter((e): e is VersionReadyEvent => e.type === 'VERSION_READY'),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.versionLista());
    // Irrecuperable: misma regla que una versión nueva (offline-fix: nunca en medio de una venta ni sin red).
    this.sw.unrecoverable
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.versionLista());

    const alVolver = () => {
      if (this.documento.visibilityState === 'visible') this.buscarVersion();
    };
    this.documento.addEventListener('visibilitychange', alVolver);
    const intervalo = setInterval(() => this.buscarVersion(), INTERVALO_BUSQUEDA_VERSION_MS);
    this.destroyRef.onDestroy(() => {
      this.documento.removeEventListener('visibilitychange', alVolver);
      clearInterval(intervalo);
    });

    this.buscarVersion();
  }

  /** El botón "Actualizar" del aviso: la persona decidió recargar. */
  actualizarAhora(): void {
    this.recargarPagina();
  }

  private versionLista(): void {
    if (this.puedeRecargarSola()) {
      this.recargarPagina();
    } else {
      this._hayVersionNueva.set(true);
    }
  }

  private buscarVersion(): void {
    // Sin red, checkForUpdate rechaza: no hay nada que hacer, se vuelve a preguntar después.
    this.sw.checkForUpdate().catch(() => {});
  }
}
