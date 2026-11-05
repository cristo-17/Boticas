import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BadgeComponent } from '../../../shared/components/badge/badge';
import { ButtonComponent } from '../../../shared/components/button/button';
import { CardComponent } from '../../../shared/components/card/card';
import { ChipComponent } from '../../../shared/components/chip/chip';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner';
import { PaginacionComponent } from '../../../shared/components/paginacion/paginacion';
import { SkeletonComponent } from '../../../shared/components/skeleton/skeleton';
import { SummaryCardComponent } from '../../../shared/components/summary-card/summary-card';
import { TableComponent } from '../../../shared/components/table/table';
import { ToastComponent, ToastVariant } from '../../../shared/components/toast/toast';
import { FilaDigemid } from '../../../core/models/reporte.model';
import { ReporteService } from '../../../core/services/reporte.service';
import { RangoRapido, fechaHoraLima, rangoRapido } from '../../../core/utils/fecha-lima.util';
import { ACCESOS_RAPIDOS, fechaCorta } from '../accesos-rapidos';

/** Rótulo fijo de la pestaña (RF-54); el servidor manda el mismo texto en `nota`. */
export const ROTULO_DIGEMID =
  'Reporte interno de dispensación con receta. No reemplaza los formatos oficiales de DIGEMID';
export const SIN_LOTE = 'Sin lote (en revisión)';
export const SIN_RECETA = 'Sin receta (en revisión)';

/**
 * Pestaña DIGEMID de Reportes (RF-54, solo ADMINISTRADOR por la ruta /reportes):
 * una fila por línea vendida con receta, totales por producto al pie y Excel.
 */
@Component({
  selector: 'app-reporte-digemid',
  imports: [
    FormsModule,
    BadgeComponent,
    ButtonComponent,
    CardComponent,
    ChipComponent,
    EmptyStateComponent,
    ErrorBannerComponent,
    PaginacionComponent,
    SkeletonComponent,
    SummaryCardComponent,
    TableComponent,
    ToastComponent,
  ],
  templateUrl: './reporte-digemid.html',
  styleUrl: './reporte-digemid.scss',
})
export class ReporteDigemidComponent implements OnInit {
  private readonly reporteService = inject(ReporteService);

  readonly reporte = this.reporteService.digemid;
  readonly cargando = this.reporteService.cargandoDigemid;
  readonly descargandoExcel = this.reporteService.descargandoExcelDigemid;
  readonly error = this.reporteService.errorDigemid;

  readonly rotulo = ROTULO_DIGEMID;
  readonly sinLote = SIN_LOTE;
  readonly sinReceta = SIN_RECETA;
  readonly accesosRapidos = ACCESOS_RAPIDOS;
  readonly fechaCorta = fechaCorta;
  readonly fechaHora = fechaHoraLima;

  readonly desde = signal('');
  readonly hasta = signal('');
  readonly rangoActivo = signal<RangoRapido>('mes');

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

  consultar(pagina = 0): void {
    this.reporteService.obtenerDigemid(this.desde(), this.hasta(), pagina).subscribe({ error: () => {} });
  }

  elegirRango(id: RangoRapido): void {
    this.rangoActivo.set(id);
    if (id === 'personalizado') return;
    const { desde, hasta } = rangoRapido(id);
    this.desde.set(desde);
    this.hasta.set(hasta);
    this.consultar();
  }

  irAPagina(pagina: number): void {
    this.consultar(pagina);
  }

  lote(f: FilaDigemid): string {
    return f.loteId === null ? SIN_LOTE : (f.loteCodigo ?? '');
  }

  descargarExcel(): void {
    const d = this.desde();
    const h = this.hasta();
    this.reporteService.descargarExcelDigemid(d, h).subscribe({
      next: (blob) => {
        if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = ReporteService.nombreArchivoExcelDigemid(d, h);
          a.click();
          URL.revokeObjectURL(url);
        }
        this.mostrarToast('success', 'Excel descargado');
      },
      error: () => this.mostrarToast('error', this.error() ?? 'No se pudo descargar el Excel'),
    });
  }

  private mostrarToast(variant: ToastVariant, mensaje: string): void {
    this.toastVariant.set(variant);
    this.toastMensaje.set(mensaje);
    this.toastVisible.set(false);
    setTimeout(() => this.toastVisible.set(true));
  }
}
