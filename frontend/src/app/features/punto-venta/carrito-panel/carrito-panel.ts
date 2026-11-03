import {
  Component,
  ElementRef,
  EventEmitter,
  Injector,
  Input,
  Output,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { ButtonComponent } from '../../../shared/components/button/button';
import { BadgeComponent } from '../../../shared/components/badge/badge';
import { ModalComponent } from '../../../shared/components/modal/modal';
import { MetodoPago } from '../../../core/models/venta.model';
import { formatearMoneda } from '../../../core/utils/moneda.util';
import { totalLinea } from '../../../core/utils/dinero.util';
import { LineaCarrito } from '../services/carrito.service';

/**
 * Columna fija a la derecha desde 1024 px. Debajo de 1024 px (celular y
 * tablet vertical), plegado es una barra de una línea ("4 líneas ·
 * S/ 33.00 · Cobrar") y se abre en una hoja inferior al tocarla
 * (Móvil y tablet, P1-P3). "Cobrar" de la barra no cobra: abre la hoja
 * con el foco en el método de pago, para ver el método antes de cobrar.
 *
 * Los totales llegan por @Input porque el IGV depende de props de
 * configuración de la venta.
 */
@Component({
  selector: 'app-carrito-panel',
  imports: [ButtonComponent, BadgeComponent, ModalComponent],
  templateUrl: './carrito-panel.html',
  styleUrl: './carrito-panel.scss',
  host: { '[class.carrito-panel--expandido]': 'expandido()' },
})
export class CarritoPanelComponent {
  @Input() lineas: LineaCarrito[] = [];
  @Input() opGravada = 0;
  @Input() igv = 0;
  @Input() total = 0;
  @Input() unidades = 0;
  @Input() mostrarIgv = true;
  @Input() igvLabel = 'IGV 18%';
  @Input() cobrando = false;

  @Output() incrementar = new EventEmitter<string>();
  @Output() decrementar = new EventEmitter<string>();
  @Output() lineaQuitada = new EventEmitter<string>();
  @Output() cobrar = new EventEmitter<MetodoPago>();
  @Output() vaciar = new EventEmitter<void>();

  /** Solo cuenta debajo de 1024 px; en la columna de escritorio todo se ve siempre. Empieza plegado. */
  readonly expandido = signal(false);
  private readonly injector = inject(Injector);
  private readonly metodos = viewChild<ElementRef<HTMLElement>>('metodos');
  readonly lineaPendiente = signal<LineaCarrito | null>(null);
  readonly confirmandoVaciar = signal(false);
  readonly metodoSeleccionado = signal<MetodoPago>('efectivo');

  readonly formatearMoneda = formatearMoneda;
  readonly totalLinea = totalLinea;

  get vacio(): boolean {
    return this.lineas.length === 0;
  }

  get resumenTexto(): string {
    return this.vacio
      ? 'Sin productos'
      : `${this.lineas.length} líneas · ${this.unidades} unidades`;
  }

  /** Texto de la barra plegada: "1 línea · S/ 4.40", "4 líneas · S/ 75.70" o "Carrito vacío". */
  get textoBarra(): string {
    if (this.vacio) return 'Carrito vacío';
    const n = this.lineas.length;
    return `${n} ${n === 1 ? 'línea' : 'líneas'} · ${formatearMoneda(this.total)}`;
  }

  /** "Cobrar" de la barra plegada: abre la hoja y pone el foco en el método de pago elegido. */
  abrirEnPago(): void {
    this.expandido.set(true);
    afterNextRender(
      () => {
        const boton = this.metodos()?.nativeElement.querySelector<HTMLButtonElement>(
          '.carrito-panel__metodo-btn--activo',
        );
        boton?.focus();
        boton?.scrollIntoView?.({ block: 'nearest' });
      },
      { injector: this.injector },
    );
  }

  pedirQuitar(linea: LineaCarrito): void {
    this.lineaPendiente.set(linea);
  }

  confirmarQuitar(): void {
    const linea = this.lineaPendiente();
    if (linea) {
      this.lineaQuitada.emit(linea.key);
    }
    this.lineaPendiente.set(null);
  }

  confirmarVaciar(): void {
    this.confirmandoVaciar.set(false);
    this.vaciar.emit();
  }
}
