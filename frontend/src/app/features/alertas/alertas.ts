import { Component, HostListener, OnDestroy, OnInit, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { BadgeComponent } from '../../shared/components/badge/badge';
import { ButtonComponent } from '../../shared/components/button/button';
import { CardComponent } from '../../shared/components/card/card';
import { ChipComponent } from '../../shared/components/chip/chip';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner';
import { PaginacionComponent } from '../../shared/components/paginacion/paginacion';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton';
import {
  SummaryCardComponent,
  SummaryCardVariant,
} from '../../shared/components/summary-card/summary-card';
import { Alerta, NivelAlerta } from '../../core/models/alerta.model';
import { AlertaService } from '../../core/services/alerta.service';
import { AuthService } from '../../core/services/auth.service';
import { ConfigService } from '../../core/services/config.service';
import { NotificacionService } from '../../core/services/notificacion.service';
import { ReporteService } from '../../core/services/reporte.service';
import { fechaLimaIso, sumarDias } from '../../core/utils/fecha-lima.util';
import { GraficoResultadosComponent } from '../../shared/components/grafico-resultados/grafico-resultados';
import { formatearMoneda } from '../../core/utils/moneda.util';

export type FiltroAlertaNivel = 'todos' | 'urgente' | 'atencion' | 'con-monto';

interface KpiVista {
  label: string;
  valor: string;
  nota: string;
  variant: SummaryCardVariant;
  ruta: string;
}

const NIVEL_BADGE: Record<NivelAlerta, 'danger' | 'warn' | 'info'> = {
  urgente: 'danger',
  atencion: 'warn',
  informativa: 'info',
};

const NIVEL_LABEL: Record<NivelAlerta, string> = {
  urgente: 'Urgente',
  atencion: 'Atención',
  informativa: 'Informativa',
};

const TAMANO_PAGINA = 20;

const FORMATO_HORA = new Intl.DateTimeFormat('es-PE', {
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'America/Lima',
});

const FORMATO_HORA_SEGUNDOS = new Intl.DateTimeFormat('es-PE', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  timeZone: 'America/Lima',
});

@Component({
  selector: 'app-alertas',
  imports: [
    BadgeComponent,
    ButtonComponent,
    CardComponent,
    ChipComponent,
    EmptyStateComponent,
    ErrorBannerComponent,
    GraficoResultadosComponent,
    PaginacionComponent,
    SkeletonComponent,
    SummaryCardComponent,
  ],
  templateUrl: './alertas.html',
  styleUrl: './alertas.scss',
})
export class AlertasScreen implements OnInit, OnDestroy {
  private readonly alertaService = inject(AlertaService);
  private readonly auth = inject(AuthService);
  private readonly config = inject(ConfigService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly router = inject(Router);
  private readonly reporteService = inject(ReporteService);

  readonly alertas = this.alertaService.alertas;
  readonly pagina = this.alertaService.pagina;
  readonly resumen = this.alertaService.resumen;
  readonly cargando = this.alertaService.cargando;
  readonly error = this.alertaService.error;
  readonly ultimaActualizacion = this.alertaService.ultimaActualizacion;
  readonly conectadoSse = this.notificaciones.conectadoSse;

  readonly nivelBadge = NIVEL_BADGE;
  readonly nivelLabel = NIVEL_LABEL;

  /**
   * Gráfico de los últimos 7 días (Bloque C): solo ADMINISTRADOR. El TECNICO no
   * ve ningún monto (conteo ciego) — ni siquiera se pide el reporte.
   */
  readonly esAdmin = computed(() => this.auth.usuarioActual()?.rol === 'ADMINISTRADOR');
  readonly tendencia = this.reporteService.tendencia;
  readonly cargandoTendencia = this.reporteService.cargandoTendencia;
  readonly errorTendencia = this.reporteService.errorTendencia;

  readonly paginaActual = signal(0);
  readonly filtroNivel = signal<FiltroAlertaNivel>('todos');

  private timerRef: ReturnType<typeof setInterval> | null = null;

  constructor() {
    // Reaccionar automáticamente a eventos SSE entrantes en tiempo real
    effect(() => {
      const live = this.notificaciones.alertaEnVivo();
      if (live) {
        this.cargarTodo(true);
      }
    });
  }

  readonly saludo = computed(() => {
    const hora = new Date().getHours();
    const saludoHora = hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches';
    const nombre = this.auth.usuarioActual()?.nombre.split(' ')[0] ?? '';
    return nombre ? `${saludoHora}, ${nombre}` : saludoHora;
  });

  readonly fechaTexto = new Date().toLocaleDateString('es-PE', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });

  readonly ultimaActualizacionTexto = computed(() =>
    FORMATO_HORA_SEGUNDOS.format(this.ultimaActualizacion()),
  );

  // Alertas filtradas reactivamente
  readonly alertasFiltradas = computed(() => {
    const f = this.filtroNivel();
    const lista = this.alertas();
    if (f === 'urgente') return lista.filter((a) => a.nivel === 'urgente');
    if (f === 'atencion') return lista.filter((a) => a.nivel === 'atencion');
    if (f === 'con-monto') return lista.filter((a) => !!a.monto);
    return lista;
  });

  readonly conteoUrgente = computed(() => this.alertas().filter((a) => a.nivel === 'urgente').length);
  readonly conteoAtencion = computed(() => this.alertas().filter((a) => a.nivel === 'atencion').length);
  readonly conteoConMonto = computed(() => this.alertas().filter((a) => !!a.monto).length);

  // KPIs del dashboard reformados
  readonly kpis = computed<KpiVista[]>(() => {
    const r = this.resumen();
    if (!r) return [];
    const criticoDias = this.config.config()?.vencimiento.criticoDias ?? 30;
    const variacion = r.ventasHoyVariacionPct ?? 0;
    const signoVariacion = variacion >= 0 ? '+' : '';
    const tarjetaVentas: KpiVista =
      r.ventasHoy !== null
        ? {
            label: 'Ventas del día',
            valor: formatearMoneda(r.ventasHoy),
            nota: `${signoVariacion}${variacion}% vs. ayer · ${r.boletasHoy} boletas`,
            variant: 'default',
            ruta: '/punto-venta',
          }
        : {
            label: 'Boletas del día',
            valor: String(r.boletasHoy),
            nota: `${r.boletasHoy === 1 ? '1 venta registrada hoy' : `${r.boletasHoy} ventas registradas hoy`}`,
            variant: 'default',
            ruta: '/punto-venta',
          };
    return [
      tarjetaVentas,
      {
        label: 'Productos por vencer',
        valor: String(r.productosPorVencer),
        nota: `${r.productosPorVencerCriticos} vencen en menos de ${criticoDias} días`,
        variant: 'warn',
        ruta: '/inventario',
      },
      {
        label: 'Stock crítico',
        valor: String(r.stockCritico),
        nota: `${r.stockAgotado} sin unidades disponibles`,
        variant: 'danger',
        ruta: '/inventario',
      },
      {
        label: 'Estado de caja',
        valor: r.cajaEstado === 'ABIERTA' ? 'Abierta' : 'Cerrada',
        nota:
          r.cajaEstado === 'ABIERTA' && r.cajaHoraApertura
            ? `Desde ${FORMATO_HORA.format(new Date(r.cajaHoraApertura))}`
            : 'Sin turno abierto',
        variant: 'info',
        ruta: '/caja',
      },
    ];
  });

  ngOnInit(): void {
    this.cargarTodo();
    this.cargarTendencia();
    // Sondeo suave periódico cada 30 segundos si la pantalla queda abierta
    this.timerRef = setInterval(() => {
      if (document.visibilityState === 'visible') {
        this.cargarTodo(true);
      }
    }, 30000);
  }

  ngOnDestroy(): void {
    if (this.timerRef) {
      clearInterval(this.timerRef);
      this.timerRef = null;
    }
  }

  @HostListener('document:visibilitychange')
  onVisibilityChange(): void {
    if (document.visibilityState === 'visible') {
      this.cargarTodo(true);
    }
  }

  cargarTodo(silencioso = false): void {
    this.alertaService.recargarTodo(this.paginaActual(), TAMANO_PAGINA, silencioso).subscribe();
  }

  actualizarManual(): void {
    this.cargarTodo(false);
    this.cargarTendencia();
  }

  /** Últimos 7 días con la fecha de Lima (hoy incluido). */
  cargarTendencia(): void {
    if (!this.esAdmin()) return;
    const hoy = fechaLimaIso();
    this.reporteService.obtenerTendencia(sumarDias(hoy, -6), hoy).subscribe({ error: () => {} });
  }

  cambiarFiltro(f: FiltroAlertaNivel): void {
    this.filtroNivel.set(f);
  }

  irAPagina(pagina: number): void {
    this.paginaActual.set(Math.max(0, pagina));
    this.cargarTodo(false);
  }

  irA(ruta: string, queryParams?: Record<string, string>): void {
    this.router.navigate([ruta], { queryParams });
  }

  accionar(alerta: Alerta): void {
    this.irA(alerta.accionRuta, alerta.accionQueryParams);
  }
}
