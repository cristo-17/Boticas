import { Component, computed, inject, signal } from '@angular/core';
import { ButtonComponent } from '../../../shared/components/button/button';
import { CardComponent } from '../../../shared/components/card/card';
import { ModalComponent } from '../../../shared/components/modal/modal';
import { ToastComponent, ToastVariant } from '../../../shared/components/toast/toast';
import { CajaService } from '../../../core/services/caja.service';
import { formatearMoneda } from '../../../core/utils/moneda.util';

interface FilaMovimiento {
  descripcion: string;
  nota: string;
  monto: number;
  colorClass:
    'cierre__barra--verde' | 'cierre__barra--azul' | 'cierre__barra--ambar' | 'cierre__barra--rojo';
  afectaEfectivo: boolean;
}

type TonoDescuadre = 'neutro' | 'ok' | 'warn' | 'danger';

interface EstadoDescuadre {
  tono: TonoDescuadre;
  titulo: string;
  montoTexto: string;
  nota: string;
}

const UMBRAL_DESCUADRE_LEVE = 10;

@Component({
  selector: 'app-cierre',
  imports: [ButtonComponent, CardComponent, ModalComponent, ToastComponent],
  templateUrl: './cierre.html',
  styleUrl: './cierre.scss',
})
export class CierreComponent {
  private readonly caja = inject(CajaService);

  readonly cajaActual = this.caja.cajaActual;
  readonly cerrando = this.caja.cargando;
  readonly efectivoEsperado = this.caja.efectivoEsperado;
  readonly formatearMoneda = formatearMoneda;

  readonly legendaDescuadre = [
    { colorClass: 'cierre__barra--verde', label: 'Sin diferencia' },
    {
      colorClass: 'cierre__barra--ambar',
      label: `Hasta ${formatearMoneda(UMBRAL_DESCUADRE_LEVE)} · requiere nota`,
    },
    { colorClass: 'cierre__barra--rojo', label: 'Más de S/ 10 · alerta al administrador' },
  ];

  // Apertura + movimientos de caja, para mostrar en la tabla de cierre
  readonly movimientosVista = computed<FilaMovimiento[]>(() => {
    const caja = this.cajaActual();
    const apertura: FilaMovimiento[] = caja
      ? [
          {
            descripcion: 'Apertura de caja',
            nota: `${this.horaTexto(caja.horaApertura)} · ${caja.usuarioNombre}`,
            monto: caja.montoApertura,
            colorClass: 'cierre__barra--verde',
            afectaEfectivo: true,
          },
        ]
      : [];
    const resto = this.caja.movimientos().map((m) => ({
      descripcion: m.descripcion,
      nota: m.nota,
      monto: m.monto,
      colorClass: this.colorPorTipo(m.tipo),
      afectaEfectivo: m.afectaEfectivo,
    }));
    return [...apertura, ...resto];
  });

  readonly contadoTexto = signal('');

  private readonly contadoNumero = computed(() =>
    parseFloat(this.contadoTexto().replace(',', '.')),
  );

  readonly contadoValido = computed(() => {
    const texto = this.contadoTexto().trim();
    return texto !== '' && !isNaN(this.contadoNumero()) && this.contadoNumero() >= 0;
  });

  readonly diferencia = computed(() =>
    this.contadoValido() ? this.contadoNumero() - this.efectivoEsperado() : null,
  );

  readonly estadoDescuadre = computed<EstadoDescuadre>(() => {
    const d = this.diferencia();
    if (d === null) {
      return {
        tono: 'neutro',
        titulo: 'Falta el conteo',
        montoTexto: '—',
        nota: 'Cuenta el efectivo del cajón para ver el descuadre.',
      };
    }
    if (Math.abs(d) < 0.005) {
      return {
        tono: 'ok',
        titulo: 'Caja cuadrada',
        montoTexto: formatearMoneda(0),
        nota: 'El conteo coincide con lo esperado.',
      };
    }
    const signo = d < 0 ? '−' : '+';
    const montoTexto = signo + formatearMoneda(Math.abs(d)).slice(3);
    if (Math.abs(d) <= UMBRAL_DESCUADRE_LEVE) {
      return {
        tono: 'warn',
        titulo: d < 0 ? 'Descuadre leve · faltante' : 'Descuadre leve · sobrante',
        montoTexto,
        nota: 'Diferencia menor a S/ 10. Explica el motivo en observaciones.',
      };
    }
    return {
      tono: 'danger',
      titulo: d < 0 ? 'Faltante importante' : 'Sobrante importante',
      montoTexto,
      nota: 'Revisa el conteo antes de cerrar: el administrador recibirá una alerta.',
    };
  });

  readonly observacionHint = computed(() =>
    this.diferencia() !== null && Math.abs(this.diferencia()!) > UMBRAL_DESCUADRE_LEVE
      ? '(recomendadas por el descuadre)'
      : '(opcional)',
  );

  readonly observacion = signal('');

  readonly confirmando = signal(false);
  readonly toastVisible = signal(false);
  readonly toastMensaje = signal('');
  readonly toastVariant = signal<ToastVariant>('success');

  onContadoInput(event: Event): void {
    this.contadoTexto.set((event.target as HTMLInputElement).value);
  }

  pedirCierre(): void {
    if (this.contadoValido()) {
      this.confirmando.set(true);
    }
  }

  confirmarCierre(): void {
    this.caja
      .cerrarCaja({
        montoContado: this.contadoNumero(),
        observaciones: this.observacion().trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.confirmando.set(false);
          this.mostrarToast(
            'success',
            `Caja cerrada · ${this.estadoDescuadre().titulo.toLowerCase()}`,
          );
          this.contadoTexto.set('');
          this.observacion.set('');
        },
        error: () => {
          this.confirmando.set(false);
          this.mostrarToast('error', 'No se pudo cerrar la caja. Reintenta.');
        },
      });
  }

  private colorPorTipo(tipo: string): FilaMovimiento['colorClass'] {
    switch (tipo) {
      case 'ingreso':
        return 'cierre__barra--azul';
      case 'egreso':
        return 'cierre__barra--ambar';
      case 'merma':
        return 'cierre__barra--rojo';
      default:
        return 'cierre__barra--verde';
    }
  }

  private horaTexto(iso: string): string {
    return new Date(iso).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' });
  }

  private mostrarToast(variant: ToastVariant, mensaje: string): void {
    this.toastVariant.set(variant);
    this.toastMensaje.set(mensaje);
    this.toastVisible.set(false);
    setTimeout(() => this.toastVisible.set(true));
  }
}
