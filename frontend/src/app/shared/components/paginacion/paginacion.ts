import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonComponent } from '../button/button';

/**
 * Control de paginación genérico para cualquier listado que consuma
 * PaginaResponse<T> (Anexo D del backend). Compuesto solo con clases
 * bs-* ya existentes (app-button, field-hint) — nada de CSS nuevo.
 */
@Component({
  selector: 'app-paginacion',
  imports: [ButtonComponent],
  templateUrl: './paginacion.html',
})
export class PaginacionComponent {
  @Input({ required: true }) pagina!: number;
  @Input({ required: true }) totalPaginas!: number;
  @Input({ required: true }) totalElementos!: number;

  @Output() anterior = new EventEmitter<void>();
  @Output() siguiente = new EventEmitter<void>();

  get esPrimera(): boolean {
    return this.pagina <= 0;
  }

  get esUltima(): boolean {
    return this.pagina + 1 >= this.totalPaginas;
  }
}
