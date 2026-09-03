import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { ButtonComponent } from '../../../shared/components/button/button';
import { BadgeComponent } from '../../../shared/components/badge/badge';
import { ModalComponent } from '../../../shared/components/modal/modal';
import { formatearMoneda } from '../../../core/utils/moneda.util';
import { LineaCarrito } from '../services/carrito.service';

/**
 * Lateral en escritorio, hoja inferior colapsable en móvil.
 *
 * Los totales llegan por @Input porque el IGV depende de props de
 * configuración de la venta.
 */
@Component({
  selector: 'app-carrito-panel',
  imports: [ButtonComponent, BadgeComponent, ModalComponent],
  templateUrl: './carrito-panel.html',
  styleUrl: './carrito-panel.scss',
})
export class CarritoPanelComponent {
  @Input() lineas: LineaCarrito[] = [];
  @Input() subtotal = 0;
  @Input() igv = 0;
  @Input() total = 0;
  @Input() unidades = 0;
  @Input() mostrarIgv = true;
  @Input() igvLabel = 'IGV 18%';
  @Input() cobrando = false;

  @Output() incrementar = new EventEmitter<string>();
  @Output() decrementar = new EventEmitter<string>();
  @Output() lineaQuitada = new EventEmitter<string>();
  @Output() cobrar = new EventEmitter<void>();

  readonly expandido = signal(true);
  readonly lineaPendiente = signal<LineaCarrito | null>(null);

  readonly formatearMoneda = formatearMoneda;

  get vacio(): boolean {
    return this.lineas.length === 0;
  }

  get resumenTexto(): string {
    return this.vacio
      ? 'Sin productos'
      : `${this.lineas.length} líneas · ${this.unidades} unidades`;
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
}
