import { Component, computed, inject, input, output, signal } from '@angular/core';
import { ButtonComponent } from '../../../shared/components/button/button';
import { CardComponent } from '../../../shared/components/card/card';
import { ModalComponent } from '../../../shared/components/modal/modal';
import { CajaService } from '../../../core/services/caja.service';
import { ConfigService } from '../../../core/services/config.service';
import { formatearMoneda } from '../../../core/utils/moneda.util';

export const MOTIVOS_EGRESO_DEFECTO = [
  'Pago a proveedor',
  'Servicios básicos',
  'Insumos y limpieza',
  'Movilidad y encomiendas',
  'Otro',
];

@Component({
  selector: 'app-egreso-caja',
  imports: [ButtonComponent, CardComponent, ModalComponent],
  templateUrl: './egreso-caja.html',
  styleUrl: './egreso-caja.scss',
})
export class EgresoCajaComponent {
  private readonly caja = inject(CajaService);
  private readonly config = inject(ConfigService);

  readonly cajaId = input<number | undefined>(undefined);
  readonly registrado = output<void>();

  readonly guardando = this.caja.cargando;
  readonly formularioAbierto = signal(false);
  readonly confirmando = signal(false);

  readonly montoTexto = signal('');
  readonly motivo = signal(MOTIVOS_EGRESO_DEFECTO[0]);
  readonly descripcion = signal('');

  readonly motivosEgreso = computed(
    () => this.config.config()?.motivosEgreso ?? MOTIVOS_EGRESO_DEFECTO,
  );

  readonly montoNumero = computed(() => parseFloat(this.montoTexto().replace(',', '.')));

  readonly montoValido = computed(() => {
    const texto = this.montoTexto().trim();
    return texto !== '' && !isNaN(this.montoNumero()) && this.montoNumero() > 0;
  });

  readonly formularioValido = computed(
    () =>
      this.montoValido() &&
      this.motivo().trim() !== '' &&
      this.descripcion().trim() !== '' &&
      this.descripcion().trim().length <= 250,
  );

  readonly tituloConfirmacion = computed(() =>
    this.montoValido()
      ? `¿Registrar gasto de ${formatearMoneda(this.montoNumero())}?`
      : '¿Registrar gasto de caja?',
  );

  readonly mensajeConfirmacion = computed(
    () =>
      `Motivo: ${this.motivo()} · Detalle: "${this.descripcion().trim()}". Un egreso es un registro contable y no se puede editar ni borrar; si hubiera un error, explícalo en las observaciones del cierre.`,
  );

  abrirFormulario(): void {
    const lista = this.motivosEgreso();
    this.motivo.set(lista[0] ?? MOTIVOS_EGRESO_DEFECTO[0]);
    this.montoTexto.set('');
    this.descripcion.set('');
    this.formularioAbierto.set(true);
  }

  cerrarFormulario(): void {
    this.formularioAbierto.set(false);
    this.confirmando.set(false);
  }

  pedirConfirmacion(): void {
    if (this.formularioValido()) {
      this.confirmando.set(true);
    }
  }

  confirmarEgreso(): void {
    if (!this.formularioValido()) return;
    this.caja
      .registrarEgreso({
        monto: this.montoNumero(),
        motivo: this.motivo(),
        descripcion: this.descripcion().trim(),
        cajaId: this.cajaId(),
      })
      .subscribe({
        next: () => {
          this.confirmando.set(false);
          this.formularioAbierto.set(false);
          this.montoTexto.set('');
          this.descripcion.set('');
          this.registrado.emit();
        },
        error: () => {
          this.confirmando.set(false);
        },
      });
  }
}
