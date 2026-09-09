import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BadgeComponent, BadgeVariant } from '../../shared/components/badge/badge';
import { ButtonComponent } from '../../shared/components/button/button';
import { CardComponent } from '../../shared/components/card/card';
import { ChipComponent } from '../../shared/components/chip/chip';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner';
import { PaginacionComponent } from '../../shared/components/paginacion/paginacion';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton';
import { TableComponent } from '../../shared/components/table/table';
import { EstadoVencimiento } from '../../core/models/estados.model';
import { Lote } from '../../core/models/lote.model';
import { Producto } from '../../core/models/producto.model';
import { ConfigService } from '../../core/services/config.service';
import { InventarioService } from '../../core/services/inventario.service';
import { ProductoService } from '../../core/services/producto.service';
import { diasHasta } from '../../core/utils/fecha.util';
import { formatearMoneda } from '../../core/utils/moneda.util';

type VencimientoFiltro = 'todos' | 'ok' | 'advertencia' | 'critico' | 'vencido';

interface FilaLote extends Lote {
  dias: number;
  badgeVariant: BadgeVariant;
  statusClass: 'status-ok' | 'status-warn' | 'status-danger';
  textoVencimiento: string;
  stockBajo: boolean;
}

const TAMANO_PAGINA = 20;

const FILTROS_VENCIMIENTO: { valor: VencimientoFiltro; label: string }[] = [
  { valor: 'todos', label: 'Todos' },
  { valor: 'ok', label: 'Más de 90 días' },
  { valor: 'advertencia', label: '31 a 90 días' },
  { valor: 'critico', label: '0 a 30 días' },
  { valor: 'vencido', label: 'Vencido' },
];

function badgeVariantDe(estado: EstadoVencimiento): BadgeVariant {
  return estado === 'OK' ? 'ok' : estado === 'ADVERTENCIA' ? 'warn' : 'danger';
}

function statusClassDe(estado: EstadoVencimiento): 'status-ok' | 'status-warn' | 'status-danger' {
  return estado === 'OK' ? 'status-ok' : estado === 'ADVERTENCIA' ? 'status-warn' : 'status-danger';
}

/** dias/badgeVariant/statusClass/texto se derivan acá porque dependen de la fecha del DISPOSITIVO (diasHasta); estadoVencimiento/stockEstado (la regla de negocio) ya vienen resueltos por el servidor y nunca se recalculan (docs/API-CONTRATO.md). */
function filaDeLote(lote: Lote): FilaLote {
  const dias = diasHasta(lote.fechaVencimiento);
  return {
    ...lote,
    dias,
    badgeVariant: badgeVariantDe(lote.estadoVencimiento),
    statusClass: statusClassDe(lote.estadoVencimiento),
    textoVencimiento: dias < 0 ? `Vencido hace ${Math.abs(dias)} d` : `En ${dias} días`,
    stockBajo: lote.stockEstado !== 'OK',
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
    ErrorBannerComponent,
    PaginacionComponent,
    SkeletonComponent,
    TableComponent,
  ],
  templateUrl: './inventario.html',
  styleUrl: './inventario.scss',
})
export class InventarioScreen implements OnInit {
  private readonly inventario = inject(InventarioService);
  private readonly productoService = inject(ProductoService);
  private readonly config = inject(ConfigService);
  private readonly route = inject(ActivatedRoute);

  readonly cargando = this.inventario.cargando;
  readonly error = this.inventario.error;
  readonly categorias = this.inventario.categorias;
  readonly formatearMoneda = formatearMoneda;
  readonly Number = Number;

  readonly filtrosVencimiento = FILTROS_VENCIMIENTO;

  // Los filtros activos se inicializan desde los query params de la URL, para que la UI pueda enlazar a /inventario?categoria=...&vencimiento=...&soloStockBajo=true y abrir la pantalla con los filtros ya aplicados.
  private readonly paramsIniciales = this.route.snapshot.queryParamMap;
  readonly categoriaSeleccionada = signal(this.paramsIniciales.get('categoria') ?? 'Todas');
  readonly vencimientoSeleccionado = signal<VencimientoFiltro>(
    (this.paramsIniciales.get('vencimiento') as VencimientoFiltro | null) ?? 'todos',
  );
  readonly soloStockBajo = signal(this.paramsIniciales.get('soloStockBajo') === 'true');
  readonly paginaActual = signal(0);

  readonly pagina = this.inventario.pagina;

  readonly filas = computed<FilaLote[]>(() => this.inventario.lotes().map(filaDeLote));

  readonly vacio = computed(() => !this.cargando() && !this.error() && this.filas().length === 0);

  readonly leyenda = [
    { statusClass: 'status-ok', label: 'Más de 90 días' },
    { statusClass: 'status-warn', label: '31 a 90 días' },
    { statusClass: 'status-danger', label: '0 a 30 días o vencido' },
  ];

  // --- Registrar lote (formulario simple con signals, sin Reactive Forms: 6 campos, un solo uso) ---
  readonly formularioAbierto = signal(false);
  readonly productosDisponibles = signal<Producto[]>([]);
  readonly nuevoProductoId = signal<number | null>(null);
  readonly nuevoCodigo = signal('');
  readonly nuevaFechaVencimiento = signal('');
  readonly nuevoStock = signal<number | null>(null);
  readonly nuevaUbicacion = signal('');
  readonly nuevoCostoUnitario = signal<number | null>(null);
  readonly registrando = this.inventario.cargando;
  readonly errorFormulario = this.inventario.error;

  readonly formularioValido = computed(
    () =>
      this.nuevoProductoId() !== null &&
      this.nuevoCodigo().trim() !== '' &&
      this.nuevaFechaVencimiento().trim() !== '' &&
      (this.nuevoStock() ?? -1) >= 0 &&
      (this.nuevoCostoUnitario() ?? -1) >= 0,
  );

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.inventario
      .listarLotes(
        {
          categoria: this.categoriaSeleccionada(),
          vencimiento: this.vencimientoSeleccionado(),
          soloStockBajo: this.soloStockBajo(),
        },
        this.paginaActual(),
        TAMANO_PAGINA,
      )
      .subscribe();
  }

  irAPagina(pagina: number): void {
    this.paginaActual.set(Math.max(0, pagina));
    this.cargar();
  }

  seleccionarCategoria(categoria: string): void {
    this.categoriaSeleccionada.set(categoria);
    this.paginaActual.set(0);
    this.cargar();
  }

  seleccionarVencimiento(valor: VencimientoFiltro): void {
    this.vencimientoSeleccionado.set(valor);
    this.paginaActual.set(0);
    this.cargar();
  }

  alternarStockBajo(): void {
    this.soloStockBajo.update((v) => !v);
    this.paginaActual.set(0);
    this.cargar();
  }

  limpiarFiltros(): void {
    this.categoriaSeleccionada.set('Todas');
    this.vencimientoSeleccionado.set('todos');
    this.soloStockBajo.set(false);
    this.paginaActual.set(0);
    this.cargar();
  }

  abrirFormulario(): void {
    this.formularioAbierto.set(true);
    this.nuevoProductoId.set(null);
    this.nuevoCodigo.set('');
    this.nuevaFechaVencimiento.set('');
    this.nuevoStock.set(null);
    this.nuevaUbicacion.set('');
    this.nuevoCostoUnitario.set(null);
    this.productoService.buscarProductos('').subscribe((productos) => {
      this.productosDisponibles.set(productos);
    });
  }

  cerrarFormulario(): void {
    this.formularioAbierto.set(false);
  }

  onProductoSeleccionado(event: Event): void {
    const valor = (event.target as HTMLSelectElement).value;
    this.nuevoProductoId.set(valor ? Number(valor) : null);
  }

  registrarLote(): void {
    const productoId = this.nuevoProductoId();
    const stock = this.nuevoStock();
    const costoUnitario = this.nuevoCostoUnitario();
    if (productoId === null || stock === null || costoUnitario === null || !this.formularioValido()) {
      return;
    }
    this.inventario
      .registrarLote({
        productoId,
        codigo: this.nuevoCodigo().trim(),
        fechaVencimiento: this.nuevaFechaVencimiento(),
        stock,
        ubicacion: this.nuevaUbicacion().trim(),
        costoUnitario,
      })
      .subscribe({
        next: () => {
          this.formularioAbierto.set(false);
          this.cargar();
        },
        error: () => {}, // el error ya queda en inventario.error() (mostrado en el formulario)
      });
  }
}
