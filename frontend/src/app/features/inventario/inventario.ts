import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BadgeComponent, BadgeVariant } from '../../shared/components/badge/badge';
import { ButtonComponent } from '../../shared/components/button/button';
import { CardComponent } from '../../shared/components/card/card';
import { ChipComponent } from '../../shared/components/chip/chip';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton';
import { TableComponent } from '../../shared/components/table/table';
import { Lote } from '../../core/models/lote.model';
import { InventarioService } from '../../core/services/inventario.service';
import { diasHasta, estadoFefo } from '../../core/utils/fecha.util';
import { formatearMoneda } from '../../core/utils/moneda.util';

type VencimientoFiltro = 'todos' | 'ok' | 'pronto' | 'critico';

interface FilaLote extends Lote {
  dias: number;
  badgeVariant: BadgeVariant;
  statusClass: 'status-ok' | 'status-warn' | 'status-danger';
  textoVencimiento: string;
  stockBajo: boolean;
}

const UMBRAL_STOCK_BAJO = 15;

const FILTROS_VENCIMIENTO: { valor: VencimientoFiltro; label: string }[] = [
  { valor: 'todos', label: 'Todos' },
  { valor: 'ok', label: 'Más de 90 días' },
  { valor: 'pronto', label: '30 a 90 días' },
  { valor: 'critico', label: 'Menos de 30 · vencido' },
];

function filaDeLote(lote: Lote): FilaLote {
  const dias = diasHasta(lote.fechaVencimiento);
  const estado = estadoFefo(dias);
  const badgeVariant: BadgeVariant =
    estado === 'ok' ? 'ok' : estado === 'pronto' ? 'warn' : 'danger';
  const statusClass =
    estado === 'ok' ? 'status-ok' : estado === 'pronto' ? 'status-warn' : 'status-danger';
  const textoVencimiento = dias < 0 ? `Vencido hace ${Math.abs(dias)} d` : `En ${dias} días`;
  return {
    ...lote,
    dias,
    badgeVariant,
    statusClass,
    textoVencimiento,
    stockBajo: lote.stock <= UMBRAL_STOCK_BAJO,
  };
}

@Component({
  selector: 'app-inventario',
  imports: [
    BadgeComponent,
    ButtonComponent,
    CardComponent,
    ChipComponent,
    EmptyStateComponent,
    SkeletonComponent,
    TableComponent,
  ],
  templateUrl: './inventario.html',
  styleUrl: './inventario.scss',
})
export class InventarioScreen implements OnInit {
  private readonly inventario = inject(InventarioService);
  private readonly route = inject(ActivatedRoute);

  readonly cargando = this.inventario.cargando;
  readonly error = this.inventario.error;
  readonly categorias = this.inventario.categorias;
  readonly formatearMoneda = formatearMoneda;

  readonly filtrosVencimiento = FILTROS_VENCIMIENTO;

  // Los filtros activos se inicializan desde los query params de la URL, para que la UI pueda enlazar a /inventario?categoria=...&vencimiento=...&soloStockBajo=true y abrir la pantalla con los filtros ya aplicados.
  private readonly paramsIniciales = this.route.snapshot.queryParamMap;
  readonly categoriaSeleccionada = signal(this.paramsIniciales.get('categoria') ?? 'Todas');
  readonly vencimientoSeleccionado = signal<VencimientoFiltro>(
    (this.paramsIniciales.get('vencimiento') as VencimientoFiltro | null) ?? 'todos',
  );
  readonly soloStockBajo = signal(this.paramsIniciales.get('soloStockBajo') === 'true');
  readonly busqueda = signal('');

  readonly filas = computed<FilaLote[]>(() => {
    const q = this.busqueda().trim().toLowerCase();
    const lista = this.inventario.lotes().map(filaDeLote);
    const filtradas = q
      ? lista.filter(
          (f) =>
            f.productoNombre.toLowerCase().includes(q) ||
            f.codigo.toLowerCase().includes(q) ||
            f.categoria.toLowerCase().includes(q) ||
            f.ubicacion.toLowerCase().includes(q),
        )
      : lista;
    return filtradas.sort((a, b) => a.dias - b.dias);
  });

  readonly vacio = computed(() => !this.cargando() && !this.error() && this.filas().length === 0);

  readonly leyenda = [
    { statusClass: 'status-ok', label: 'Más de 90 días' },
    { statusClass: 'status-warn', label: '30 a 90 días' },
    { statusClass: 'status-danger', label: 'Menos de 30 días o vencido' },
  ];

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.inventario
      .listarLotes({
        categoria: this.categoriaSeleccionada(),
        vencimiento: this.vencimientoSeleccionado(),
        soloStockBajo: this.soloStockBajo(),
      })
      .subscribe();
  }

  seleccionarCategoria(categoria: string): void {
    this.categoriaSeleccionada.set(categoria);
    this.cargar();
  }

  seleccionarVencimiento(valor: VencimientoFiltro): void {
    this.vencimientoSeleccionado.set(valor);
    this.cargar();
  }

  alternarStockBajo(): void {
    this.soloStockBajo.update((v) => !v);
    this.cargar();
  }

  onBusquedaInput(event: Event): void {
    this.busqueda.set((event.target as HTMLInputElement).value);
  }

  limpiarFiltros(): void {
    this.busqueda.set('');
    this.categoriaSeleccionada.set('Todas');
    this.vencimientoSeleccionado.set('todos');
    this.soloStockBajo.set(false);
    this.cargar();
  }
}
