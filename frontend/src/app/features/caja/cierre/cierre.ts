import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ButtonComponent } from '../../../shared/components/button/button';
import { CardComponent } from '../../../shared/components/card/card';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner';
import { ModalComponent } from '../../../shared/components/modal/modal';
import { PaginacionComponent } from '../../../shared/components/paginacion/paginacion';
import { ToastComponent, ToastVariant } from '../../../shared/components/toast/toast';
import { ErrorTraducido } from '../../../core/interceptors/error.interceptor';
import { CajaService } from '../../../core/services/caja.service';
import { ConfigService } from '../../../core/services/config.service';
import { formatearMoneda } from '../../../core/utils/moneda.util';

interface FilaMovimiento {
  descripcion: string;
  nota: string | null;
  monto: number;
  colorClass:
    'cierre__barra--verde' | 'cierre__barra--azul' | 'cierre__barra--ambar' | 'cierre__barra--rojo';
  afectaEfectivo: boolean;
}

type TonoDescuadre = 'ok' | 'warn' | 'danger';

interface ResultadoCierre {
  tono: TonoDescuadre;
  titulo: string;
  montoTexto: string;
  nota: string;
  montoEsperado: number;
  montoContado: number;
}

/** Fallback si /api/config no llegó a cargar todavía (no debería pasar: provideAppInitializer la espera antes de arrancar la app). */
const UMBRAL_DESCUADRE_LEVE_DEFECTO = 10;
const TAMANO_PAGINA_MOVIMIENTOS = 20;

@Component({
  selector: 'app-cierre',
  imports: [
    ButtonComponent,
    CardComponent,
    ErrorBannerComponent,
    ModalComponent,
    PaginacionComponent,
    ToastComponent,
  ],
  templateUrl: './cierre.html',
  styleUrl: './cierre.scss',
})
export class CierreComponent implements OnInit {
  private readonly caja = inject(CajaService);
  private readonly config = inject(ConfigService);

  readonly cajaActual = this.caja.cajaActual;
  readonly cerrando = this.caja.cargando;
  readonly resumenCierre = this.caja.resumenCierre;
  readonly paginaMovimientos = this.caja.movimientos;
  // Regla de frontend (CLAUDE.md): la pantalla muestra el error del servicio que llama.
  readonly error = this.caja.error;
  readonly formatearMoneda = formatearMoneda;

  private readonly umbralDescuadreLeve = computed(
    () => this.config.config()?.descuadreLeve ?? UMBRAL_DESCUADRE_LEVE_DEFECTO,
  );

  readonly legendaDescuadre = computed(() => [
    { colorClass: 'cierre__barra--verde', label: 'Sin diferencia' },
    {
      colorClass: 'cierre__barra--ambar',
      label: `Hasta ${formatearMoneda(this.umbralDescuadreLeve())} · requiere nota`,
    },
    {
      colorClass: 'cierre__barra--rojo',
      label: `Más de ${formatearMoneda(this.umbralDescuadreLeve())} · alerta al administrador`,
    },
  ]);

  // Apertura + movimientos de caja (paginados), para la tabla de la izquierda. Ya NO incluye
  // el efectivo esperado: conteo ciego (hueco 2, docs/DECISIONES.md) — eso solo se conoce
  // después de POST /api/caja/cerrar, nunca antes de que el cajero cuente.
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
    const resto = (this.paginaMovimientos()?.contenido ?? []).map((m) => ({
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

  // Se llena recién con la respuesta de POST /api/caja/cerrar -- nunca antes (conteo ciego).
  readonly resultadoCierre = computed<ResultadoCierre | null>(() => {
    const caja = this.cajaActual();
    if (!caja || caja.abierta || !caja.semaforoDescuadre) return null;
    const esperado = caja.montoEsperado ?? 0;
    const contado = caja.montoContado ?? 0;
    const d = caja.diferencia ?? 0;
    const signo = d < 0 ? '−' : d > 0 ? '+' : '';
    const montoTexto = signo + formatearMoneda(Math.abs(d)).slice(3);
    if (caja.semaforoDescuadre === 'EXACTO') {
      return {
        tono: 'ok',
        titulo: 'Caja cuadrada',
        montoTexto: formatearMoneda(0),
        nota: 'El conteo coincide con lo esperado.',
        montoEsperado: esperado,
        montoContado: contado,
      };
    }
    if (caja.semaforoDescuadre === 'LEVE') {
      return {
        tono: 'warn',
        titulo: d < 0 ? 'Descuadre leve · faltante' : 'Descuadre leve · sobrante',
        montoTexto,
        nota: `Diferencia menor a ${formatearMoneda(this.umbralDescuadreLeve())}.`,
        montoEsperado: esperado,
        montoContado: contado,
      };
    }
    return {
      tono: 'danger',
      titulo: d < 0 ? 'Faltante importante' : 'Sobrante importante',
      montoTexto,
      nota: 'El administrador recibirá una alerta por este descuadre.',
      montoEsperado: esperado,
      montoContado: contado,
    };
  });

  readonly observacionHint = computed(() => (this.resultadoCierre() ? '' : '(opcional)'));

  readonly observacion = signal('');

  readonly confirmando = signal(false);
  readonly toastVisible = signal(false);
  readonly toastMensaje = signal('');
  readonly toastVariant = signal<ToastVariant>('success');

  ngOnInit(): void {
    // No asume que CajaScreen (el padre) ya haya resuelto obtenerCajaDeHoy():
    // se vuelve a pedir acá (GET idempotente) y recién con la respuesta en
    // mano se sabe si hay una caja abierta para pedirle resumen-cierre.
    // El error ya queda en caja.error() (mostrado por app-error-banner);
    // los callbacks vacíos solo evitan el "unhandled error" de RxJS.
    this.caja.obtenerCajaDeHoy().subscribe({
      next: () => this.cargarDatosDeCierre(),
      error: () => {},
    });
  }

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
            `Caja cerrada · ${this.resultadoCierre()?.titulo?.toLowerCase() ?? ''}`,
          );
          this.contadoTexto.set('');
          this.observacion.set('');
        },
        error: (err: HttpErrorResponse & { traducido?: ErrorTraducido }) => {
          this.confirmando.set(false);
          this.mostrarToast('error', err.traducido?.mensaje ?? 'No se pudo cerrar la caja. Reintenta.');
        },
      });
  }

  irAPagina(pagina: number): void {
    this.caja.listarMovimientos(pagina, TAMANO_PAGINA_MOVIMIENTOS).subscribe({ error: () => {} });
  }

  private cargarDatosDeCierre(): void {
    if (!this.cajaActual()?.abierta) return;
    this.caja.obtenerResumenCierre().subscribe({ error: () => {} });
    this.irAPagina(0);
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
