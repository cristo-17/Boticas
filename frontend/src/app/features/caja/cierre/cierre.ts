import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner';
import { PaginacionComponent } from '../../../shared/components/paginacion/paginacion';
import { ToastComponent, ToastVariant } from '../../../shared/components/toast/toast';
import { ErrorTraducido } from '../../../core/interceptors/error.interceptor';
import { MovimientoCaja } from '../../../core/models/caja.model';
import { CajaService } from '../../../core/services/caja.service';
import { ConfigService } from '../../../core/services/config.service';
import { ColaVentasService } from '../../../core/services/cola-ventas.service';
import { ConexionService } from '../../../core/services/conexion.service';
import { formatearMoneda } from '../../../core/utils/moneda.util';
import { ConteoCierre, ConteoCierreComponent } from '../conteo-cierre/conteo-cierre';
import { EgresoCajaComponent } from '../egreso-caja/egreso-caja';
import {
  ResultadoCierreComponent,
  UMBRAL_DESCUADRE_LEVE_DEFECTO,
  resultadoDeCierre,
} from '../resultado-cierre/resultado-cierre';

interface FilaMovimiento {
  descripcion: string;
  nota: string | null;
  /** null mientras la caja está abierta: conteo ciego (DECISIONES 2026-10-14). */
  monto: number | null;
  colorClass:
    'cierre__barra--verde' | 'cierre__barra--azul' | 'cierre__barra--ambar' | 'cierre__barra--rojo';
  afectaEfectivo: boolean;
}

const TAMANO_PAGINA_MOVIMIENTOS = 20;

@Component({
  selector: 'app-cierre',
  imports: [
    ConteoCierreComponent,
    EgresoCajaComponent,
    ErrorBannerComponent,
    PaginacionComponent,
    ResultadoCierreComponent,
    ToastComponent,
  ],
  templateUrl: './cierre.html',
  styleUrl: './cierre.scss',
})
export class CierreComponent implements OnInit {
  private readonly caja = inject(CajaService);
  private readonly config = inject(ConfigService);
  readonly cola = inject(ColaVentasService);
  private readonly conexion = inject(ConexionService);

  /**
   * Tarea 22 (§8): ventas de ESTA caja que siguen en la cola de este dispositivo.
   * Solo la cantidad, nunca su monto (conteo ciego). Mientras haya alguna no se
   * puede cerrar: una venta en efectivo que llega después del cierre le deja un
   * descuadre falso a una caja ya contada.
   */
  readonly pendientesDeCaja = computed(() => {
    const caja = this.cajaActual();
    return caja?.abierta ? this.cola.cantidadDeCaja(caja.id) : 0;
  });

  readonly cajaActual = this.caja.cajaActual;
  readonly cerrando = this.caja.cargando;
  readonly resumenCierre = this.caja.resumenCierre;
  readonly paginaMovimientos = this.caja.movimientos;
  // Regla de frontend (CLAUDE.md): la pantalla muestra el error del servicio que llama.
  readonly error = this.caja.error;
  readonly formatearMoneda = formatearMoneda;

  /**
   * Conteo ciego (DECISIONES 2026-09-08, revisada 2026-10-14): mientras la caja
   * está abierta la lista muestra hora, tipo y método, SIN montos -- una lista
   * con montos es el esperado listo para sumar. Después del POST, completa.
   */
  readonly mostrarMontos = computed(() => this.cajaActual()?.abierta === false);

  readonly movimientosVista = computed<FilaMovimiento[]>(() => {
    const caja = this.cajaActual();
    const conMontos = this.mostrarMontos();
    const apertura: FilaMovimiento[] = caja
      ? [
          {
            descripcion: 'Apertura de caja',
            nota: `${this.horaTexto(caja.horaApertura)} · ${caja.usuarioNombre}`,
            monto: conMontos ? caja.montoApertura : null,
            colorClass: 'cierre__barra--verde',
            afectaEfectivo: true,
          },
        ]
      : [];
    const resto = (this.paginaMovimientos()?.contenido ?? []).map((m) => ({
      descripcion: m.descripcion,
      nota: [this.horaTexto(m.creadoEn), this.metodo(m), m.nota].filter(Boolean).join(' · '),
      monto: conMontos ? m.monto : null,
      colorClass: this.colorPorTipo(m.tipo),
      afectaEfectivo: m.afectaEfectivo,
    }));
    return [...apertura, ...resto];
  });

  private readonly conteo = viewChild(ConteoCierreComponent);

  /** Solo para el texto del toast: la tarjeta de resultado la arma ResultadoCierreComponent. */
  private readonly resultadoCierre = computed(() =>
    resultadoDeCierre(this.cajaActual(), this.config.config()?.descuadreLeve ?? UMBRAL_DESCUADRE_LEVE_DEFECTO),
  );

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
      next: () => {
        this.cargarDatosDeCierre();
        // Tarea 22 (§8): con la caja en mano, se intenta vaciar la cola primero.
        void this.enviarPendientes();
      },
      error: () => {},
    });
  }

  /** "Enviar ahora": comprueba la conexión, vacía la cola y recarga el resumen (que cuenta las que llegaron). */
  async enviarPendientes(): Promise<void> {
    await this.cola.cargar();
    if (this.pendientesDeCaja() === 0) return;
    await this.conexion.comprobar();
    await this.cola.enviarPendientes();
    this.cargarDatosDeCierre();
  }

  // POST /api/caja/cerrar sin cajaId: la caja propia (D-E2-1).
  cerrar(conteo: ConteoCierre): void {
    this.caja.cerrarCaja(conteo).subscribe({
      next: () => {
        this.conteo()?.terminar(true);
        this.mostrarToast('success', `Caja cerrada · ${this.resultadoCierre()?.titulo?.toLowerCase() ?? ''}`);
      },
      error: (err: HttpErrorResponse & { traducido?: ErrorTraducido }) => {
        this.conteo()?.terminar(false);
        this.mostrarToast('error', err.traducido?.mensaje ?? 'No se pudo cerrar la caja. Reintenta.');
      },
    });
  }

  irAPagina(pagina: number): void {
    this.caja.listarMovimientos(pagina, TAMANO_PAGINA_MOVIMIENTOS).subscribe({ error: () => {} });
  }

  onGastoRegistrado(): void {
    this.cargarDatosDeCierre();
    this.mostrarToast('success', 'Gasto registrado');
  }

  private cargarDatosDeCierre(): void {
    if (!this.cajaActual()?.abierta) return;
    this.caja.obtenerResumenCierre().subscribe({ error: () => {} });
    this.irAPagina(0);
  }

  /** El movimiento no guarda el medio de pago, solo si toca el cajón: alcanza para el conteo. */
  private metodo(m: MovimientoCaja): string | null {
    if (m.tipo === 'venta') return m.afectaEfectivo ? 'Efectivo' : 'Digital (Yape/tarjeta)';
    if (m.tipo === 'merma') return null;
    return m.afectaEfectivo ? 'Efectivo' : null;
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
