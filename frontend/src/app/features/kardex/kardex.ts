import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, switchMap } from 'rxjs/operators';
import { BadgeComponent, BadgeVariant } from '../../shared/components/badge/badge';
import { ButtonComponent } from '../../shared/components/button/button';
import { CardComponent } from '../../shared/components/card/card';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner';
import { PaginacionComponent } from '../../shared/components/paginacion/paginacion';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton';
import { SummaryCardComponent } from '../../shared/components/summary-card/summary-card';
import { TableComponent } from '../../shared/components/table/table';
import { ToastComponent, ToastVariant } from '../../shared/components/toast/toast';
import { ProductoCatalogo } from '../../core/models/catalogo.model';
import { FilaKardex } from '../../core/models/kardex.model';
import { CatalogoService } from '../../core/services/catalogo.service';
import { FiltroKardex, KardexService } from '../../core/services/kardex.service';
import { fechaHoraLima, fechaLimaIso } from '../../core/utils/fecha-lima.util';

const TAMANO_PAGINA = 50;

/** Mismo texto que el .xlsx del servidor (KardexExcel.etiquetaTipo). */
export function etiquetaTipoKardex(f: FilaKardex): string {
  switch (f.tipo) {
    case 'SALDO_ANTERIOR':
      return 'Saldo anterior';
    case 'ENTRADA':
      return f.entradaInicial ? 'Entrada inicial (registrada por la migración)' : 'Entrada (lote)';
    case 'SALIDA_RECETA':
      return 'Salida con receta';
    case 'SALIDA_LIBRE':
      return 'Salida libre';
    case 'MERMA':
      return 'Merma';
  }
}

const VARIANTE_TIPO: Record<FilaKardex['tipo'], BadgeVariant> = {
  SALDO_ANTERIOR: 'neutral',
  ENTRADA: 'ok',
  SALIDA_LIBRE: 'neutral',
  SALIDA_RECETA: 'info',
  MERMA: 'warn',
};

/**
 * Kardex por producto, solo ADMINISTRADOR (Tarea 20, D-E2-5). Ruta con
 * rolGuard; el servidor exige el rol igual. El saldo lo calcula el servidor:
 * la pantalla solo muestra (R19).
 */
@Component({
  selector: 'app-kardex',
  imports: [
    BadgeComponent,
    ButtonComponent,
    CardComponent,
    EmptyStateComponent,
    ErrorBannerComponent,
    PaginacionComponent,
    SkeletonComponent,
    SummaryCardComponent,
    TableComponent,
    ToastComponent,
  ],
  templateUrl: './kardex.html',
  styleUrl: './kardex.scss',
})
export class KardexScreen implements OnInit {
  private readonly kardexService = inject(KardexService);
  private readonly catalogo = inject(CatalogoService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly kardex = this.kardexService.kardex;
  readonly cargando = this.kardexService.cargando;
  readonly descargandoExcel = this.kardexService.descargandoExcel;
  readonly opciones = this.catalogo.productos;
  readonly buscandoProductos = this.catalogo.cargando;
  /** Regla de frontend 1: dos servicios, un banner. */
  readonly error = computed(() => this.kardexService.error() ?? this.catalogo.error());

  readonly etiquetaTipo = etiquetaTipoKardex;
  readonly fechaHora = fechaHoraLima;

  // --- Filtros (estado local de la pantalla) ---
  readonly productoId = signal<number | null>(null);
  readonly busqueda = signal('');
  readonly mostrarOpciones = signal(false);
  readonly desde = signal('');
  readonly hasta = signal('');
  private readonly pagina = signal(0);

  readonly hoy = fechaLimaIso();

  /** El invariante solo se puede mirar si el rango llega hasta hoy. */
  readonly rangoHastaHoy = computed(() => !this.hasta() || this.hasta() >= this.hoy);
  readonly cuadra = computed(() => {
    const k = this.kardex();
    return k ? k.resumen.saldoFinal === k.resumen.stockActual : true;
  });

  readonly toastVisible = signal(false);
  readonly toastMensaje = signal('');
  readonly toastVariant = signal<ToastVariant>('success');

  // Regla 4 ([FE-008]): búsqueda y cargas con switchMap; la búsqueda además con debounce.
  private readonly busqueda$ = new Subject<string>();
  private readonly cargas$ = new Subject<void>();

  constructor() {
    this.cargas$
      .pipe(
        filter(() => this.productoId() !== null),
        switchMap(() => this.kardexService.consultar(this.filtro(), this.pagina(), TAMANO_PAGINA)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ error: () => {} }); // el error queda en kardexService.error()
    this.busqueda$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((texto) => this.catalogo.listar(texto, 0, 8)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ error: () => {} }); // el error queda en catalogo.error()
  }

  ngOnInit(): void {
    // Borde de entrada (regla 3): el query param es string; se convierte una sola vez.
    const crudo = this.route.snapshot.queryParamMap.get('productoId');
    const id = crudo !== null ? Number(crudo) : NaN;
    if (Number.isInteger(id) && id > 0) {
      this.productoId.set(id);
      this.cargas$.next();
    } else {
      this.kardexService.limpiar();
    }
  }

  onBusqueda(event: Event): void {
    const texto = (event.target as HTMLInputElement).value;
    this.busqueda.set(texto);
    this.mostrarOpciones.set(texto.trim() !== '');
    this.busqueda$.next(texto.trim());
  }

  elegir(p: ProductoCatalogo): void {
    this.productoId.set(p.id);
    this.busqueda.set('');
    this.mostrarOpciones.set(false);
    this.pagina.set(0);
    this.kardexService.limpiar();
    this.router.navigate([], { queryParams: { productoId: p.id }, replaceUrl: true });
    this.cargas$.next();
  }

  onFecha(campo: 'desde' | 'hasta', event: Event): void {
    (campo === 'desde' ? this.desde : this.hasta).set((event.target as HTMLInputElement).value);
  }

  consultar(): void {
    this.pagina.set(0);
    this.cargas$.next();
  }

  limpiarFechas(): void {
    this.desde.set('');
    this.hasta.set('');
    this.consultar();
  }

  irAPagina(p: number): void {
    this.pagina.set(Math.max(0, p));
    this.cargas$.next();
  }

  descargarExcel(): void {
    if (this.productoId() === null) return;
    const filtro = this.filtro();
    this.kardexService.descargarExcel(filtro).subscribe({
      next: (blob) => {
        if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = KardexService.nombreArchivoExcel(filtro);
          a.click();
          URL.revokeObjectURL(url);
        }
        this.mostrarToast('success', 'Excel del Kardex descargado');
      },
      error: () => this.mostrarToast('error', this.kardexService.error() ?? 'No se pudo descargar el Excel'),
    });
  }

  variante(f: FilaKardex): BadgeVariant {
    return VARIANTE_TIPO[f.tipo];
  }

  entrada(f: FilaKardex): number | null {
    return f.cantidad !== null && f.cantidad > 0 ? f.cantidad : null;
  }

  salida(f: FilaKardex): number | null {
    return f.cantidad !== null && f.cantidad < 0 ? -f.cantidad : null;
  }

  referencia(f: FilaKardex): string {
    if (f.ventaId !== null) return `Venta #${f.ventaId}${f.recetaNumero ? ' · Receta ' + f.recetaNumero : ''}`;
    if (f.mermaId !== null) return `Merma #${f.mermaId}`;
    return '';
  }

  /** YYYY-MM-DD → dd/MM/yyyy (solo presentación). */
  fechaCorta(iso: string | null): string {
    if (!iso) return '';
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }

  private filtro(): FiltroKardex {
    return { productoId: this.productoId() as number, desde: this.desde() || undefined, hasta: this.hasta() || undefined };
  }

  private mostrarToast(variant: ToastVariant, mensaje: string): void {
    this.toastVariant.set(variant);
    this.toastMensaje.set(mensaje);
    this.toastVisible.set(false);
    setTimeout(() => this.toastVisible.set(true));
  }
}
