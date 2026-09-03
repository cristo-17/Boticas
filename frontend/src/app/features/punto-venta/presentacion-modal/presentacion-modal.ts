import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonComponent } from '../../../shared/components/button/button';
import { PresentacionProducto, Producto } from '../../../core/models/producto.model';
import { formatearMoneda } from '../../../core/utils/moneda.util';

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
}
