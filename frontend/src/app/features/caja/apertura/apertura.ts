import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { ButtonComponent } from '../../../shared/components/button/button';
import { CardComponent } from '../../../shared/components/card/card';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner';
import { AuthService } from '../../../core/services/auth.service';
import { CajaService } from '../../../core/services/caja.service';
import { formatearMoneda } from '../../../core/utils/moneda.util';

const MONTOS_RAPIDOS = [100, 150, 200, 300];

@Component({
  selector: 'app-apertura',
  imports: [ButtonComponent, CardComponent, ErrorBannerComponent],
  templateUrl: './apertura.html',
  styleUrl: './apertura.scss',
})
export class AperturaComponent {
  private readonly caja = inject(CajaService);
  private readonly auth = inject(AuthService);

  @Output() irACierre = new EventEmitter<void>();

  readonly cajaActual = this.caja.cajaActual;
  readonly abriendo = this.caja.cargando;
  // Regla de frontend (CLAUDE.md): la pantalla muestra el error del servicio que llama.
  readonly error = this.caja.error;
  readonly montosRapidos = MONTOS_RAPIDOS;
  readonly formatearMoneda = formatearMoneda;

  readonly turnoActivo = computed(() => this.auth.usuarioActual()?.turno ?? '—');

  readonly horaAperturaTexto = computed(() => {
    const hora = this.cajaActual()?.horaApertura;
    return hora
      ? new Date(hora).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })
      : '—';
  });

  readonly montoTexto = signal('');

  private readonly montoNumero = computed(() => parseFloat(this.montoTexto().replace(',', '.')));

  readonly montoValido = computed(() => {
    const texto = this.montoTexto().trim();
    return texto !== '' && !isNaN(this.montoNumero()) && this.montoNumero() >= 0;
  });

  readonly montoErrorTexto = computed(() => {
    const texto = this.montoTexto().trim();
    if (texto === '') return 'Ingresa el monto con el que abres la caja.';
    if (isNaN(this.montoNumero())) return 'Escribe solo números, por ejemplo 150.00';
    if (this.montoNumero() < 0) return 'El monto no puede ser negativo.';
    return '';
  });

  readonly aperturaMeta = computed(() => {
    const usuario = this.auth.usuarioActual();
    const ahora = new Date().toLocaleString('es-PE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
    return [
      { k: 'Fecha y hora', v: ahora },
      { k: 'Responsable', v: usuario?.nombre ?? 'Sin sesión' },
      { k: 'Turno', v: usuario?.turno ?? '—' },
      { k: 'Sede', v: usuario?.sede ?? '—' },
    ];
  });

  onMontoInput(event: Event): void {
    this.montoTexto.set((event.target as HTMLInputElement).value);
  }

  fijarMonto(valor: number): void {
    this.montoTexto.set(valor.toFixed(2));
  }

  abrirCaja(): void {
    if (!this.montoValido()) return;
    const turno = this.auth.usuarioActual()?.turno ?? 'Tarde';
    // El error ya queda en caja.error() (mostrado por app-error-banner);
    // el callback vacío solo evita el "unhandled error" que RxJS tira a
    // la consola cuando un subscribe no declara ninguno.
    this.caja.abrirCaja({ montoInicial: this.montoNumero(), turno }).subscribe({ error: () => {} });
  }
}
