import { Component, OnInit, inject, signal } from '@angular/core';
import { BadgeComponent, BadgeVariant } from '../../shared/components/badge/badge';
import { ButtonComponent } from '../../shared/components/button/button';
import { CardComponent } from '../../shared/components/card/card';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner';
import { PaginacionComponent } from '../../shared/components/paginacion/paginacion';
import { RevisionVenta, TipoRevision } from '../../core/models/venta.model';
import { RevisionVentaService } from '../../core/services/revision-venta.service';
import { formatearMoneda } from '../../core/utils/moneda.util';
import { fechaHoraLima } from '../../core/utils/fecha-lima.util';

const TAMANO_PAGINA = 20;

export const ETIQUETA_TIPO_REVISION: Record<TipoRevision, string> = {
  STOCK_INSUFICIENTE: 'Stock insuficiente',
  PRECIO_DISTINTO: 'Precio distinto',
  CAJA_CERRADA: 'Caja ya cerrada',
  OTRO: 'Otra observación',
};

const VARIANTE_TIPO: Record<TipoRevision, BadgeVariant> = {
  STOCK_INSUFICIENTE: 'warn',
  PRECIO_DISTINTO: 'info',
  CAJA_CERRADA: 'warn',
  OTRO: 'neutral',
};

/**
 * Tarea 22 (D-E2-7): "Ventas por revisar", solo ADMINISTRADOR (rolGuard; el
 * servidor exige el rol igual). Cada venta cobrada sin conexión que tuvo un
 * problema al llegar está acá; ninguna se pierde en silencio. VISTA la
 * cierra; FALTANTE (solo stock insuficiente) da las unidades por faltante de
 * inventario y exige una nota.
 */
@Component({
  selector: 'app-revisiones',
  imports: [BadgeComponent, ButtonComponent, CardComponent, EmptyStateComponent, ErrorBannerComponent, PaginacionComponent],
  templateUrl: './revisiones.html',
  styleUrl: './revisiones.scss',
})
export class RevisionesScreen implements OnInit {
  private readonly service = inject(RevisionVentaService);

  readonly pagina = this.service.pagina;
  readonly cargando = this.service.cargando;
  readonly error = this.service.error;

  readonly filtro = signal<'PENDIENTE' | 'RESUELTA'>('PENDIENTE');
  /** Nota que se está escribiendo, por revisión (estado local de la pantalla). */
  readonly notas = signal<Record<number, string>>({});

  readonly formatearMoneda = formatearMoneda;
  readonly fechaHora = fechaHoraLima;
  readonly etiquetaTipo = (t: TipoRevision) => ETIQUETA_TIPO_REVISION[t];
  readonly varianteTipo = (t: TipoRevision) => VARIANTE_TIPO[t];

  ngOnInit(): void {
    this.cargar(0);
  }

  elegirFiltro(f: 'PENDIENTE' | 'RESUELTA'): void {
    this.filtro.set(f);
    this.cargar(0);
  }

  cargar(pagina: number): void {
    this.service.listar(this.filtro(), pagina, TAMANO_PAGINA).subscribe({ error: () => {} });
  }

  escribirNota(id: number, evento: Event): void {
    const valor = (evento.target as HTMLTextAreaElement).value;
    this.notas.update((n) => ({ ...n, [id]: valor }));
  }

  notaDe(id: number): string {
    return this.notas()[id] ?? '';
  }

  marcarVista(r: RevisionVenta): void {
    this.service.resolver(r.id, 'VISTA', this.notaDe(r.id).trim() || null).subscribe({ error: () => {} });
  }

  darPorFaltante(r: RevisionVenta): void {
    this.service.resolver(r.id, 'FALTANTE', this.notaDe(r.id).trim() || null).subscribe({ error: () => {} });
  }
}
