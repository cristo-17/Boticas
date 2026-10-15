import { Component, computed, inject, input, output, signal } from '@angular/core';
import { ButtonComponent } from '../../../shared/components/button/button';
import { CardComponent } from '../../../shared/components/card/card';
import { ModalComponent } from '../../../shared/components/modal/modal';
import { ConfigService } from '../../../core/services/config.service';
import { formatearMoneda } from '../../../core/utils/moneda.util';
import { UMBRAL_DESCUADRE_LEVE_DEFECTO } from '../resultado-cierre/resultado-cierre';

export interface ConteoCierre {
  montoContado: number;
  observaciones?: string;
}

/**
 * Formulario de conteo ciego + confirmación. No conoce ni muestra el
 * esperado: solo recoge lo contado y lo emite al confirmar. Lo usan el
 * cierre del TECNICO (su caja) y el del ADMINISTRADOR (la caja elegida).
 */
@Component({
  selector: 'app-conteo-cierre',
  imports: [ButtonComponent, CardComponent, ModalComponent],
  templateUrl: './conteo-cierre.html',
  styleUrl: './conteo-cierre.scss',
})
export class ConteoCierreComponent {
  private readonly config = inject(ConfigService);

  /** true mientras viaja el POST: el modal muestra "cargando" y no se puede cancelar. */
  readonly cerrando = input(false);
  readonly confirmado = output<ConteoCierre>();

  readonly contadoTexto = signal('');
  readonly observacion = signal('');
  readonly confirmando = signal(false);

  private readonly contadoNumero = computed(() => parseFloat(this.contadoTexto().replace(',', '.')));

  readonly contadoValido = computed(() => {
    const texto = this.contadoTexto().trim();
    return texto !== '' && !isNaN(this.contadoNumero()) && this.contadoNumero() >= 0;
  });

  private readonly umbralDescuadreLeve = computed(
    () => this.config.config()?.descuadreLeve ?? UMBRAL_DESCUADRE_LEVE_DEFECTO,
  );

  readonly legendaDescuadre = computed(() => [
    { colorClass: 'conteo__barra--verde', label: 'Sin diferencia' },
    { colorClass: 'conteo__barra--ambar', label: `Hasta ${formatearMoneda(this.umbralDescuadreLeve())} · requiere nota` },
    { colorClass: 'conteo__barra--rojo', label: `Más de ${formatearMoneda(this.umbralDescuadreLeve())} · alerta al administrador` },
  ]);

  onContadoInput(event: Event): void {
    this.contadoTexto.set((event.target as HTMLInputElement).value);
  }

  pedirCierre(): void {
    if (this.contadoValido()) {
      this.confirmando.set(true);
    }
  }

  confirmar(): void {
    this.confirmado.emit({
      montoContado: this.contadoNumero(),
      observaciones: this.observacion().trim() || undefined,
    });
  }

  /** El padre la llama al terminar el POST: cierra el modal y, si salió bien, limpia el formulario. */
  terminar(exito: boolean): void {
    this.confirmando.set(false);
    if (exito) {
      this.contadoTexto.set('');
      this.observacion.set('');
    }
  }
}
