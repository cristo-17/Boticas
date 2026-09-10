import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { BadgeComponent } from '../../shared/components/badge/badge';
import { CardComponent } from '../../shared/components/card/card';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton';
import { ToastComponent } from '../../shared/components/toast/toast';
import { PresentacionProducto, Producto } from '../../core/models/producto.model';
import { MetodoPago, OrigenCaptura } from '../../core/models/venta.model';
import { ProductoService } from '../../core/services/producto.service';
import { VentaService } from '../../core/services/venta.service';
import { ConfigService } from '../../core/services/config.service';
import { formatearMoneda } from '../../core/utils/moneda.util';
import { textoAlertaVencimiento } from '../../core/utils/fecha.util';
import { CarritoPanelComponent } from './carrito-panel/carrito-panel';
import { PresentacionModalComponent } from './presentacion-modal/presentacion-modal';
import { EstadoEscaneo, ScannerComponent } from './scanner/scanner';
import { CarritoService } from './services/carrito.service';

@Component({
  selector: 'app-punto-venta',
  providers: [CarritoService], // estado de la venta en curso: se reinicia al salir de la pantalla
  imports: [
    BadgeComponent,
    CardComponent,
    ErrorBannerComponent,
    SkeletonComponent,
    ToastComponent,
    CarritoPanelComponent,
    PresentacionModalComponent,
    ScannerComponent,
  ],
  templateUrl: './punto-venta.html',
  styleUrl: './punto-venta.scss',
})
export class PuntoVentaScreen implements OnInit {
  private readonly productoService = inject(ProductoService);
  private readonly ventaService = inject(VentaService);
  private readonly configService = inject(ConfigService);
  readonly carrito = inject(CarritoService);

  readonly resultados = this.productoService.resultados;
  readonly masVendidos = this.productoService.masVendidos;
  readonly productoSeleccionado = this.productoService.seleccionado;
  readonly buscandoProductos = this.productoService.cargando;
  readonly cobrando = this.ventaService.cargando;

  /** Regla de frontend (CLAUDE.md): toda pantalla que llama a un servicio muestra su error — acá hay dos servicios, un solo banner combinado. */
  readonly error = computed(() => this.productoService.error() ?? this.ventaService.error());

  readonly query = signal('');
  readonly estadoEscaneo = signal<EstadoEscaneo>('idle');
  readonly codigoEscaneado = signal<string | null>(null);

  /**
   * De dónde salió el producto actualmente seleccionado (D4/Nota 2) —
   * no es una pantalla ni un campo que el cajero vea o elija: se
   * detecta en simularEscaneo()/elegirProducto() y viaja con la línea
   * recién al agregarla al carrito (agregarPresentacion).
   */
  private readonly origenSeleccionActual = signal<OrigenCaptura>('BUSQUEDA');

  readonly toastVisible = signal(false);
  readonly toastMensaje = signal('');

  readonly sinResultados = computed(
    () => this.query().trim() !== '' && this.resultados().length === 0,
  );

  readonly formatearMoneda = formatearMoneda;
  readonly igvLabel = computed(() => {
    const tasa = this.configService.config()?.igv;
    return tasa != null ? `IGV ${(tasa * 100).toFixed(0)}%` : 'IGV';
  });

  /**
   * Búsqueda reactiva (regla CLAUDE.md: debounceTime + switchMap):
   * sin switchMap, la respuesta de una búsqueda vieja (p. ej. "par")
   * puede llegar después que la de la búsqueda completa ("paracetamol")
   * y pisarla en pantalla — el cajero ve el catálogo entero, y un clic
   * en ese momento agrega al carrito un producto que no buscó.
   */
  private readonly busqueda$ = new Subject<string>();

  constructor() {
    this.busqueda$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((query) => this.productoService.buscarProductos(query)),
        takeUntilDestroyed(),
      )
      .subscribe({ error: () => {} });
  }

  ngOnInit(): void {
    this.productoService.obtenerMasVendidos().subscribe({ error: () => {} });
    this.busqueda$.next('');
  }

  onQueryInput(event: Event): void {
    const valor = (event.target as HTMLInputElement).value;
    this.query.set(valor);
    this.busqueda$.next(valor);
  }

  simularEscaneo(): void {
    const candidatos = this.masVendidos();
    const elegido = candidatos[Math.floor(Math.random() * candidatos.length)];
    if (!elegido) {
      this.simularFallo();
      return;
    }
    this.estadoEscaneo.set('buscando');
    this.codigoEscaneado.set(null);
    this.origenSeleccionActual.set('ESCANEO');
    this.productoService.buscarPorCodigoBarras(elegido.codigoBarras).subscribe({
      next: (producto) => {
        this.codigoEscaneado.set(elegido.codigoBarras);
        this.estadoEscaneo.set(producto ? 'encontrado' : 'error');
      },
      // PRODUCTO_NO_ENCONTRADO es un código silencioso (sin toast) pero la
      // pantalla igual necesita reflejar el fallo del escaneo.
      error: () => {
        this.codigoEscaneado.set(elegido.codigoBarras);
        this.estadoEscaneo.set('error');
      },
    });
  }

  simularFallo(): void {
    this.productoService.cerrarSeleccion();
    this.estadoEscaneo.set('error');
    this.codigoEscaneado.set('error');
  }

  /** MANUAL (Nota 2): match exacto entre lo tecleado en el buscador y el código de barras — no es una pantalla aparte, es una detección. Cualquier otra selección desde resultados es BUSQUEDA. */
  elegirProducto(producto: Producto): void {
    this.origenSeleccionActual.set(this.query().trim() === producto.codigoBarras ? 'MANUAL' : 'BUSQUEDA');
    this.productoService.seleccionar(producto);
    this.estadoEscaneo.set('encontrado');
  }

  cerrarModalPresentacion(): void {
    this.productoService.cerrarSeleccion();
    this.estadoEscaneo.set('idle');
  }

  agregarPresentacion(presentacion: PresentacionProducto): void {
    const producto = this.productoSeleccionado();
    if (producto) {
      this.carrito.agregar(producto, presentacion, this.origenSeleccionActual());
    }
    this.productoService.cerrarSeleccion();
    this.estadoEscaneo.set('idle');
  }

  precioDesde(producto: Producto): number {
    return producto.presentaciones[producto.presentaciones.length - 1]?.precio ?? 0;
  }

  alertaVencimientoDe(producto: Producto): string | null {
    return textoAlertaVencimiento(producto.estadoVencimiento, producto.fechaVencimiento);
  }

  cobrar(metodo: MetodoPago = 'efectivo'): void {
    if (this.carrito.vacio()) return;
    const items = this.carrito.lineas().map((l) => ({
      productoId: l.productoId,
      presentacionId: l.presentacionId,
      cantidad: l.cantidad,
      origenCaptura: l.origenCaptura,
    }));
    this.ventaService.registrarVenta({ items, metodoPago: metodo }).subscribe({
      next: (venta) => {
        this.carrito.vaciar();
        this.mostrarToast(
          venta.sincronizada
            ? `Venta cobrada (${metodo.toUpperCase()}) · ${formatearMoneda(venta.total)}`
            : `Venta guardada localmente · ${formatearMoneda(venta.total)}`,
        );
      },
      error: () => {}, // el error ya queda en productoService.error()/ventaService.error() (mostrado en el banner)
    });
  }

  private mostrarToast(mensaje: string): void {
    this.toastMensaje.set(mensaje);
    this.toastVisible.set(false);
    setTimeout(() => this.toastVisible.set(true));
  }
}
