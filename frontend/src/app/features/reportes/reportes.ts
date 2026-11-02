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

/** Fecha YYYY-MM-DD en zona America/Lima. */
function fechaLimaIso(fecha: Date): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(fecha);
  const y = parts.find((p) => p.type === 'year')?.value ?? '2026';
  const m = parts.find((p) => p.type === 'month')?.value ?? '01';
  const d = parts.find((p) => p.type === 'day')?.value ?? '01';
  return `${y}-${m}-${d}`;
}

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
  readonly descargandoCsv = this.reporteService.descargandoCsv;
  readonly error = this.reporteService.error;
  readonly formatearMoneda = formatearMoneda;

  readonly desde = signal('');
  readonly hasta = signal('');
  readonly agrupar = signal<AgrupacionReporte>('dia');

  readonly toastVisible = signal(false);
  readonly toastMensaje = signal('');
  readonly toastVariant = signal<ToastVariant>('success');

  constructor() {
    const hoyIso = fechaLimaIso(new Date());
    const inicioMesIso = `${hoyIso.slice(0, 8)}01`;
    this.desde.set(inicioMesIso);
    this.hasta.set(hoyIso);
  }

  ngOnInit(): void {
    this.consultar();
  }

  consultar(): void {
    this.reporteService
      .obtenerResultados(this.desde(), this.hasta(), this.agrupar())
      .subscribe({ error: () => {} });
  }

  cambiarAgrupacion(nueva: AgrupacionReporte): void {
    if (this.agrupar() === nueva) return;
    this.agrupar.set(nueva);
    this.consultar();
  }

  exportarCsv(): void {
    const d = this.desde();
    const h = this.hasta();
    const ag = this.agrupar();
    this.reporteService.descargarCsv(d, h, ag).subscribe({
      next: (blob) => {
        if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `reporte-resultados-${d}-a-${h}.csv`;
          a.click();
          URL.revokeObjectURL(url);
        }
        this.mostrarToast('success', 'CSV exportado');
      },
      error: () => {
        this.mostrarToast('error', this.error() ?? 'No se pudo exportar el CSV');
      },
    });
  }

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
