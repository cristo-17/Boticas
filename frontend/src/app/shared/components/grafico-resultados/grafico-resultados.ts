import {
  Component,
  ElementRef,
  OnDestroy,
  effect,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { FilaResultado } from '../../../core/models/reporte.model';
import { formatearMoneda } from '../../../core/utils/moneda.util';

type ModuloGrafico = typeof import('./chartjs-resultados');
type GraficoChart = ReturnType<ModuloGrafico['crearGraficoResultados']>;

/**
 * Ingresos (barras) contra gastos (barras) por período, con el resultado
 * neto en línea. Pinta exactamente los montos que manda el servidor
 * (R19): no suma ni deriva nada. Chart.js se carga con `import()` la
 * primera vez que hay datos (Tarea 18, Bloque C).
 * Usado por Resultados y por el Dashboard (≥2 pantallas → shared/).
 */
@Component({
  selector: 'app-grafico-resultados',
  template: `
    <div class="grafico" [style.height.px]="alto()">
      <canvas #lienzo role="img" [attr.aria-label]="descripcion()"></canvas>
      @if (sinSoporte()) {
        <p class="grafico__aviso field-hint">No se pudo dibujar el gráfico en este navegador.</p>
      }
    </div>
  `,
  styles: `
    .grafico { position: relative; width: 100%; }
    .grafico__aviso { position: absolute; inset: 0; display: grid; place-items: center; margin: 0; }
  `,
})
export class GraficoResultadosComponent implements OnDestroy {
  readonly filas = input.required<FilaResultado[]>();
  readonly alto = input(280);
  readonly compacto = input(false);

  readonly sinSoporte = signal(false);

  private readonly lienzo = viewChild.required<ElementRef<HTMLCanvasElement>>('lienzo');
  private grafico: GraficoChart | null = null;
  private destruido = false;

  readonly descripcion = () => {
    const n = this.filas().length;
    return `Gráfico de ingresos contra gastos y resultado neto, ${n} ${n === 1 ? 'período' : 'períodos'}`;
  };

  constructor() {
    effect(() => {
      const filas = this.filas();
      const compacto = this.compacto();
      const canvas = this.lienzo().nativeElement;
      void this.dibujar(canvas, filas, compacto);
    });
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.grafico?.destroy();
    this.grafico = null;
  }

  private async dibujar(canvas: HTMLCanvasElement, filas: FilaResultado[], compacto: boolean): Promise<void> {
    // jsdom (tests) y navegadores sin canvas: no hay contexto 2D, no se carga Chart.js.
    if (typeof canvas.getContext !== 'function' || !this.tieneContexto(canvas)) {
      this.sinSoporte.set(true);
      return;
    }
    const modulo = await import('./chartjs-resultados');
    if (this.destruido) return;
    this.grafico?.destroy();
    const css = getComputedStyle(canvas);
    const token = (nombre: string, respaldo: string) => css.getPropertyValue(nombre).trim() || respaldo;
    this.grafico = modulo.crearGraficoResultados(
      canvas,
      {
        etiquetas: filas.map((f) => f.etiqueta),
        ingresos: filas.map((f) => f.ventas),
        gastos: filas.map((f) => f.gastos),
        resultado: filas.map((f) => f.resultado),
      },
      {
        compacto,
        formatearMoneda,
        colores: {
          ingresos: token('--verde-500', '#1e885a'),
          gastos: token('--ambar-500', '#e07a2e'),
          resultado: token('--azul-600', '#2c4a8a'),
          texto: token('--muted', '#6b7280'),
          rejilla: token('--line', '#e5e7eb'),
        },
      },
    );
  }

  private tieneContexto(canvas: HTMLCanvasElement): boolean {
    try {
      return canvas.getContext('2d') !== null;
    } catch {
      return false;
    }
  }
}
