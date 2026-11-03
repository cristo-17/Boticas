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
import { MetodoPago, OrigenCaptura, RecetaVenta } from '../../core/models/venta.model';
import { ProductoService } from '../../core/services/producto.service';
import { VentaService } from '../../core/services/venta.service';
import { ConfigService } from '../../core/services/config.service';
import { formatearMoneda } from '../../core/utils/moneda.util';
import { textoAlertaVencimiento } from '../../core/utils/fecha.util';
import { CarritoPanelComponent } from './carrito-panel/carrito-panel';
import { PresentacionModalComponent } from './presentacion-modal/presentacion-modal';
import { RecetaModalComponent } from './receta-modal/receta-modal';
import { CarritoService } from './services/carrito.service';
import { EstadoEscaneo, LectorCamaraComponent } from '../../shared/components/lector-camara/lector-camara';
import { DetectorLectorUsb, RELOJ } from '../../core/utils/lector-usb';

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
    RecetaModalComponent,
    LectorCamaraComponent,
  ],
  templateUrl: './punto-venta.html',
  styleUrl: './punto-venta.scss',
})
export class PuntoVentaScreen implements OnInit {
  private readonly productoService = inject(ProductoService);
  readonly ventaService = inject(VentaService);
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
   * detecta en buscarCodigoLeido() (cámara o lector USB: ESCANEO) y en
   * elegirProducto() (Nota 2), y viaja con la línea recién al agregarla al
   * carrito (agregarPresentacion).
   */
  private readonly origenSeleccionActual = signal<OrigenCaptura>('BUSQUEDA');

  /** DIF-05 (DECISIONES 2026-11-03, "D4 revisada"): un lector USB que teclea en el buscador también es ESCANEO. */
  private readonly detectorUsb = new DetectorLectorUsb();
  private readonly reloj = inject(RELOJ);

  /** Tarea 20: método elegido mientras se piden los datos de la receta (null = modal cerrado). */
  readonly metodoConReceta = signal<MetodoPago | null>(null);

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
    // Pegar, autocompletar o la sugerencia del teclado del celular no es una ráfaga de lector (DIF-05).
    if ((event as InputEvent).inputType !== 'insertText') this.detectorUsb.cambioAjeno();
    const valor = (event.target as HTMLInputElement).value;
    this.query.set(valor);
    this.busqueda$.next(valor);
  }

  /** Keydown del buscador: si la ráfaga es de un lector USB (DIF-05), busca el código como un escaneo. */
  onQueryKeydown(event: KeyboardEvent): void {
    const codigo = this.detectorUsb.tecla(event, this.reloj());
    if (!codigo) return;
    event.preventDefault();
    // El lector tecleó el código en el campo: se limpia (y con él la búsqueda por texto en curso).
    this.query.set('');
    this.busqueda$.next('');
    this.buscarCodigoLeido(codigo);
  }

  /**
   * Un código que leyó una máquina (cámara o lector USB) y ya pasó el dígito
   * verificador: ESCANEO. Si existe, el modal de presentación se abre solo
   * (seleccionado del servicio); si no, el banner muestra el error.
   */
  buscarCodigoLeido(codigo: string): void {
    this.estadoEscaneo.set('buscando');
    this.codigoEscaneado.set(codigo);
    this.origenSeleccionActual.set('ESCANEO');
    this.productoService.buscarPorCodigoBarras(codigo).subscribe({
      next: () => this.estadoEscaneo.set('encontrado'),
      // PRODUCTO_NO_ENCONTRADO es silencioso (sin toast), pero el banner y el marco lo reflejan.
      error: () => this.estadoEscaneo.set('error'),
    });
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

  /**
   * Tarea 20: si alguna línea requiere receta, primero se piden sus datos
   * (modal); el servidor igual la exige (400 RECETA_REQUERIDA).
   */
  cobrar(metodo: MetodoPago = 'efectivo'): void {
    if (this.carrito.vacio()) return;
    if (this.carrito.productosConReceta().length > 0) {
      this.metodoConReceta.set(metodo);
      return;
    }
    this.enviarVenta(metodo, null);
  }

  cobrarConReceta(receta: RecetaVenta): void {
    const metodo = this.metodoConReceta();
    if (metodo) this.enviarVenta(metodo, receta);
  }

  private enviarVenta(metodo: MetodoPago, receta: RecetaVenta | null): void {
    const items = this.carrito.lineas().map((l) => ({
      productoId: l.productoId,
      presentacionId: l.presentacionId,
      cantidad: l.cantidad,
      origenCaptura: l.origenCaptura,
    }));
    this.ventaService.registrarVenta({ items, metodoPago: metodo, receta }).subscribe({
      next: (venta) => {
        this.metodoConReceta.set(null);
        this.carrito.vaciar();
        this.mostrarToast(
          venta.sincronizada
            ? `Venta cobrada (${metodo.toUpperCase()}) · ${formatearMoneda(venta.total)}`
            : `Venta guardada localmente · ${formatearMoneda(venta.total)}`,
        );
      },
      error: () => {}, // el error ya queda en ventaService.error(): banner de la pantalla y, si está abierto, el modal de receta
    });
  }

  private mostrarToast(mensaje: string): void {
    this.toastMensaje.set(mensaje);
    this.toastVisible.set(false);
    setTimeout(() => this.toastVisible.set(true));
  }
}
