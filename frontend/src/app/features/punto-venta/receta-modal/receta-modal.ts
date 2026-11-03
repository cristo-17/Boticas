import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, computed, signal } from '@angular/core';
import { ButtonComponent } from '../../../shared/components/button/button';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner';
import { RecetaVenta } from '../../../core/models/venta.model';
import { fechaLimaIso } from '../../../core/utils/fecha-lima.util';

/**
 * Datos de la receta al cobrar (Tarea 20, D-E2-4). Se abre solo si alguna
 * línea del carrito es de un producto con requiereReceta. Número y CMP
 * obligatorios; médico y fecha de emisión opcionales. Las mismas reglas las
 * vuelve a aplicar el servidor (400 RECETA_REQUERIDA / RECETA_INVALIDA).
 */
@Component({
  selector: 'app-receta-modal',
  imports: [ButtonComponent, ErrorBannerComponent],
  templateUrl: './receta-modal.html',
  styleUrl: './receta-modal.scss',
})
export class RecetaModalComponent implements OnChanges {
  @Input() abierto = false;
  /** Nombres de los productos del carrito que piden receta. */
  @Input() productos: string[] = [];
  @Input() enviando = false;
  /** Regla de frontend 1: el error de la venta también se ve con el modal abierto. */
  @Input() error: string | null = null;

  @Output() confirmado = new EventEmitter<RecetaVenta>();
  @Output() cancelado = new EventEmitter<void>();

  readonly numero = signal('');
  readonly cmp = signal('');
  readonly medico = signal('');
  readonly fecha = signal('');
  readonly intento = signal(false);
  readonly hoy = signal(fechaLimaIso());

  readonly errores = computed<string[]>(() => {
    const errores: string[] = [];
    if (!this.numero().trim()) errores.push('Falta el número de receta.');
    if (!/^\d{1,6}$/.test(this.cmp().trim())) errores.push('El CMP del médico son de 1 a 6 dígitos.');
    if (this.fecha() && this.fecha() > this.hoy()) errores.push('La fecha de emisión no puede ser futura.');
    return errores;
  });

  ngOnChanges(cambios: SimpleChanges): void {
    // Cada cobro empieza con el formulario vacío.
    if (cambios['abierto'] && this.abierto) {
      this.numero.set('');
      this.cmp.set('');
      this.medico.set('');
      this.fecha.set('');
      this.intento.set(false);
      this.hoy.set(fechaLimaIso());
    }
  }

  confirmar(): void {
    this.intento.set(true);
    if (this.errores().length > 0) return;
    this.confirmado.emit({
      numero: this.numero().trim(),
      cmp: this.cmp().trim(),
      medicoNombre: this.medico().trim() || null,
      fechaEmision: this.fecha() || null,
    });
  }
}
