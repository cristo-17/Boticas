import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BadgeComponent } from '../../shared/components/badge/badge';
import { ButtonComponent } from '../../shared/components/button/button';
import { CardComponent } from '../../shared/components/card/card';
import { ChipComponent } from '../../shared/components/chip/chip';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton';
import { SummaryCardComponent } from '../../shared/components/summary-card/summary-card';
import { ToastComponent, ToastVariant } from '../../shared/components/toast/toast';
import { AgrupacionReporte } from '../../core/models/reporte.model';
import { ReporteService } from '../../core/services/reporte.service';
import { formatearMoneda } from '../../core/utils/moneda.util';
import { RangoRapido, rangoRapido } from '../../core/utils/fecha-lima.util';
import { ACCESOS_RAPIDOS, fechaCorta } from './accesos-rapidos';
import { ReporteDigemidComponent } from './reporte-digemid/reporte-digemid';

type PestanaReporte = 'resultados' | 'digemid';

import { GraficoResultadosComponent } from '../../shared/components/grafico-resultados/grafico-resultados';

@Component({
  selector: 'app-reportes',
  imports: [
    FormsModule,
    BadgeComponent,
    ButtonComponent,
    CardComponent,
    ChipComponent,
    EmptyStateComponent,
    ErrorBannerComponent,
    GraficoResultadosComponent,
    ReporteDigemidComponent,
    SkeletonComponent,
    SummaryCardComponent,
    ToastComponent,
  ],
  templateUrl: './reportes.html',
  styleUrl: './reportes.scss',
})
export class ReportesScreen implements OnInit {
  private readonly reporteService = inject(ReporteService);

  readonly reporte = this.reporteService.reporte;
  readonly cargando = this.reporteService.cargando;
  readonly descargandoExcel = this.reporteService.descargandoExcel;
  readonly error = this.reporteService.error;
  readonly formatearMoneda = formatearMoneda;

  readonly desde = signal('');
  readonly hasta = signal('');
  readonly agrupar = signal<AgrupacionReporte>('dia');
  readonly rangoActivo = signal<RangoRapido>('mes');
  readonly accesosRapidos = ACCESOS_RAPIDOS;
  /** RF-54: la pestaña DIGEMID consulta recién al abrirse. */
  readonly pestana = signal<PestanaReporte>('resultados');

  readonly toastVisible = signal(false);
  readonly toastMensaje = signal('');
  readonly toastVariant = signal<ToastVariant>('success');

  constructor() {
    const { desde, hasta } = rangoRapido('mes');
    this.desde.set(desde);
    this.hasta.set(hasta);
  }

  ngOnInit(): void {
    this.consultar();
  }

  consultar(): void {
    this.reporteService
      .obtenerResultados(this.desde(), this.hasta(), this.agrupar())
      .subscribe({ error: () => {} });
  }

  /** Accesos rápidos: fechas del calendario de Lima. "Personalizado" solo abre los campos. */
  elegirRango(id: RangoRapido): void {
    this.rangoActivo.set(id);
    if (id === 'personalizado') return;
    const { desde, hasta } = rangoRapido(id);
    this.desde.set(desde);
    this.hasta.set(hasta);
    this.consultar();
  }

  cambiarAgrupacion(nueva: AgrupacionReporte): void {
    if (this.agrupar() === nueva) return;
    this.agrupar.set(nueva);
    this.consultar();
  }

  descargarExcel(): void {
    const d = this.desde();
    const h = this.hasta();
    this.reporteService.descargarExcel(d, h, this.agrupar()).subscribe({
      next: (blob) => {
        if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = ReporteService.nombreArchivoExcel(d, h);
          a.click();
          URL.revokeObjectURL(url);
        }
        this.mostrarToast('success', 'Excel descargado');
      },
      error: () => {
        this.mostrarToast('error', this.error() ?? 'No se pudo descargar el Excel');
      },
    });
  }

  readonly fechaCorta = fechaCorta;

  formatearConSigno(valor: number): string {
    if (valor < 0) {
      return `−${formatearMoneda(-valor)}`;
    }
    return formatearMoneda(valor);
  }

  private mostrarToast(variant: ToastVariant, mensaje: string): void {
    this.toastVariant.set(variant);
    this.toastMensaje.set(mensaje);
    this.toastVisible.set(false);
    setTimeout(() => this.toastVisible.set(true));
  }
}
