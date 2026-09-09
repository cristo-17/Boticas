import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { BadgeComponent, BadgeVariant } from '../../shared/components/badge/badge';
import { ButtonComponent } from '../../shared/components/button/button';
import { CardComponent } from '../../shared/components/card/card';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner';
import { PaginacionComponent } from '../../shared/components/paginacion/paginacion';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton';
import { TableComponent } from '../../shared/components/table/table';
import { Venta } from '../../core/models/venta.model';
import { VentaService } from '../../core/services/venta.service';
import { formatearMoneda } from '../../core/utils/moneda.util';

const METODO_BADGE: Record<string, BadgeVariant> = {
  efectivo: 'ok',
  yape: 'info',
  tarjeta: 'neutral',
};

const METODO_LABEL: Record<string, string> = {
  efectivo: 'Efectivo',
  yape: 'Yape / Plin',
  tarjeta: 'Tarjeta',
};

@Component({
  selector: 'app-historial-ventas',
  imports: [
    BadgeComponent,
    ButtonComponent,
    CardComponent,
    EmptyStateComponent,
    ErrorBannerComponent,
    PaginacionComponent,
    SkeletonComponent,
    TableComponent,
  ],
  templateUrl: './historial-ventas.html',
  styleUrl: './historial-ventas.scss',
})
export class HistorialVentasScreen implements OnInit {
  private readonly ventaService = inject(VentaService);
  private readonly router = inject(Router);

  readonly paginaActual = signal(0);
  readonly tamanoPagina = 15;
  readonly ventaSeleccionada = signal<Venta | null>(null);
  readonly modalDetalleAbierto = signal(false);

  readonly cargando = this.ventaService.cargando;
  readonly error = this.ventaService.error;
  readonly pagina = this.ventaService.pagina;

  readonly ventas = computed(() => this.pagina()?.contenido ?? []);
  readonly totalElementos = computed(() => this.pagina()?.totalElementos ?? 0);

  readonly totalFacturadoPagina = computed(() =>
    this.ventas().reduce((acc, v) => acc + v.total, 0),
  );

  ngOnInit(): void {
    this.cargarVentas();
  }

  cargarVentas(): void {
    this.ventaService
      .listarVentas(this.paginaActual(), this.tamanoPagina, 'fecha,desc')
      .subscribe();
  }

  onCambiarPagina(nuevaPagina: number): void {
    this.irAPagina(nuevaPagina);
  }

  irAPagina(nuevaPagina: number): void {
    if (nuevaPagina < 0) return;
    this.paginaActual.set(nuevaPagina);
    this.cargarVentas();
  }

  verDetalle(venta: Venta): void {
    this.ventaSeleccionada.set(venta);
    this.modalDetalleAbierto.set(true);
  }

  cerrarDetalle(): void {
    this.modalDetalleAbierto.set(false);
    this.ventaSeleccionada.set(null);
  }

  irAPuntoVenta(): void {
    this.router.navigate(['/punto-venta']);
  }

  badgeMetodo(metodo: string): BadgeVariant {
    return METODO_BADGE[metodo] ?? 'neutral';
  }

  labelMetodo(metodo: string): string {
    return METODO_LABEL[metodo] ?? metodo;
  }

  formatearFechaHora(fechaIso: string): string {
    if (!fechaIso) return '-';
    const d = new Date(fechaIso);
    return d.toLocaleString('es-PE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  moneda(valor: number): string {
    return formatearMoneda(valor);
  }
}
