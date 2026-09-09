import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonComponent } from '../../../shared/components/button/button';
import { PresentacionProducto, Producto } from '../../../core/models/producto.model';
import { formatearMoneda } from '../../../core/utils/moneda.util';
import { textoAlertaVencimiento } from '../../../core/utils/fecha.util';

/** Modal para seleccionar la presentación de un producto. */
@Component({
  selector: 'app-presentacion-modal',
  imports: [ButtonComponent],
  templateUrl: './presentacion-modal.html',
  styleUrl: './presentacion-modal.scss',
})
export class PresentacionModalComponent {
  @Input() producto: Producto | null = null;

  @Output() elegida = new EventEmitter<PresentacionProducto>();
  @Output() cerrado = new EventEmitter<void>();

  readonly formatearMoneda = formatearMoneda;

  /**
   * Sin campo "detalle" compuesto (hueco 5): el backend manda
   * factorConversion/unidadNombre por separado, este getter arma el
   * texto ("100 tabletas") — getter plano, no computed(), porque lee un
   * @Input (CLAUDE.md, "Zoneless").
   */
  detalleDe(presentacion: PresentacionProducto): string {
    if (presentacion.factorConversion === 1) {
      return `1 ${presentacion.unidadNombre}`;
    }
    const terminaEnVocal = /[aeiouáéíóú]$/i.test(presentacion.unidadNombre);
    const plural = presentacion.unidadNombre + (terminaEnVocal ? 's' : 'es');
    return `${presentacion.factorConversion} ${plural}`;
  }

  /** Getter plano (no computed()): lee un @Input (CLAUDE.md, "Zoneless"). */
  get textoAlertaVencimiento(): string | null {
    return textoAlertaVencimiento(this.producto?.estadoVencimiento, this.producto?.fechaVencimiento);
  }
}
