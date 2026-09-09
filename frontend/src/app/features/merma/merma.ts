import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { ButtonComponent } from '../../shared/components/button/button';
import { CardComponent } from '../../shared/components/card/card';
import { ChipComponent } from '../../shared/components/chip/chip';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner';
import { ModalComponent } from '../../shared/components/modal/modal';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton';
import { ToastComponent, ToastVariant } from '../../shared/components/toast/toast';
import { MotivoMerma } from '../../core/models/merma.model';
import { Producto } from '../../core/models/producto.model';
import { AuthService } from '../../core/services/auth.service';
import { InventarioService } from '../../core/services/inventario.service';
import { MermaService } from '../../core/services/merma.service';
import { ProductoService } from '../../core/services/producto.service';
import { formatearMoneda } from '../../core/utils/moneda.util';
import { toSignal } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-merma',
  imports: [ReactiveFormsModule, ButtonComponent, CardComponent, ChipComponent, ErrorBannerComponent, ModalComponent, SkeletonComponent, ToastComponent],
  templateUrl: './merma.html',
  styleUrl: './merma.scss',
})
export class MermaScreen implements OnInit {
  private readonly productoService = inject(ProductoService);
  private readonly inventario = inject(InventarioService);
  private readonly mermaService = inject(MermaService);
  private readonly auth = inject(AuthService);

  readonly motivos = this.mermaService.motivosMerma;
  readonly formatearMoneda = formatearMoneda;
  readonly registrando = this.mermaService.cargando;

  /** Regla de frontend (CLAUDE.md): toda pantalla que llama a un servicio muestra su error — acá hay dos servicios, un solo banner combinado. */
  readonly error = computed(() => this.productoService.error() ?? this.inventario.error() ?? this.mermaService.error());

  // --- Paso 1: buscar producto (rediseño Bloque C, docs/API-CONTRATO.md) ---
  // Mismo patrón que dejamos en CLAUDE.md al corregir [FE-008]: debounceTime + switchMap, copiado de PuntoVentaScreen, no reinventado.
  readonly productoQuery = signal('');
  readonly resultadosProducto = this.productoService.resultados;
  readonly buscandoProducto = this.productoService.cargando;
  private readonly busquedaProducto$ = new Subject<string>();

  /** Producto elegido en el paso 1 -- estado local de la pantalla (no del servicio): es un paso de esta cascada, no un producto en un modal de venta como en Punto de Venta. */
  readonly productoSeleccionado = signal<Producto | null>(null);

  constructor() {
    this.busquedaProducto$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((query) => this.productoService.buscarProductos(query)),
        takeUntilDestroyed(),
      )
      .subscribe({ error: () => {} });
  }

  // --- Paso 2: lote del producto elegido (GET /api/lotes?productoId=) ---
  readonly lotesDelProducto = computed(() => this.inventario.lotes());
  readonly loteOpciones = computed(() =>
    this.lotesDelProducto().map((l) => ({ value: l.id, label: l.codigo })),
  );
  readonly loteIdSeleccionado = signal<number | null>(null);
  readonly loteSeleccionado = computed(() => {
    const loteId = this.loteIdSeleccionado();
    return this.lotesDelProducto().find((l) => l.id === loteId) ?? null;
  });

  // El resto del formulario (sin ids) sí es un FormGroup reactivo — se conecta a app-field en la plantilla.
  readonly form = new FormGroup({
    cantidad: new FormControl(1, [Validators.required, Validators.min(1)]),
    motivo: new FormControl<MotivoMerma>('Vencimiento', {
      nonNullable: true,
      validators: Validators.required,
    }),
    observacion: new FormControl(''),
  });

  readonly confirmando = signal(false);

  private readonly formValue = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  readonly cantidadValida = computed(() => {
    const lote = this.loteSeleccionado();
    const cantidad = this.formValue().cantidad ?? 0;
    return !!lote && cantidad >= 1 && cantidad <= lote.stock;
  });

  readonly cantidadErrorTexto = computed(() => {
    const lote = this.loteSeleccionado();
    const cantidad = this.formValue().cantidad ?? 0;
    if (!lote) return '';
    if (cantidad < 1) return 'Ingresa una cantidad de 1 o más.';
    if (cantidad > lote.stock) return `El lote solo tiene ${lote.stock} unidades.`;
    return '';
  });

  readonly observacionRequerida = computed(() => {
    const motivo = this.formValue().motivo;
    return !!motivo && this.mermaService.motivosQueRequierenObservacion.includes(motivo);
  });

  readonly observacionValida = computed(
    () => !this.observacionRequerida() || (this.formValue().observacion ?? '').trim().length > 0,
  );

  readonly valorMerma = computed(() => {
    const lote = this.loteSeleccionado();
    const cantidad = this.formValue().cantidad ?? 0;
    return lote && this.cantidadValida() ? cantidad * lote.precioUnitario : 0;
  });

  readonly stockDespues = computed(() => {
    const lote = this.loteSeleccionado();
    if (!lote) return 0;
    const cantidad = this.cantidadValida() ? (this.formValue().cantidad ?? 0) : 0;
    return lote.stock - cantidad;
  });

  readonly formularioValido = computed(
    () => !!this.loteSeleccionado() && this.cantidadValida() && this.observacionValida(),
  );

  readonly responsableTexto = computed(() => {
    const usuario = this.auth.usuarioActual();
    const ahora = new Date().toLocaleString('es-PE', {
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
    return `${usuario?.nombre ?? 'Sin sesión'} · ${ahora}`;
  });

  readonly toastVisible = signal(false);
  readonly toastMensaje = signal('');
  readonly toastVariant = signal<ToastVariant>('success');

  ngOnInit(): void {
    this.busquedaProducto$.next('');
  }

  onProductoQueryInput(event: Event): void {
    const valor = (event.target as HTMLInputElement).value;
    this.productoQuery.set(valor);
    this.busquedaProducto$.next(valor);
  }

  elegirProducto(producto: Producto): void {
    this.productoSeleccionado.set(producto);
    this.loteIdSeleccionado.set(null);
    this.form.controls.cantidad.setValue(1);
    this.inventario.listarLotesDeProducto(producto.id).subscribe({
      next: (pagina) => {
        const primerLote = pagina.contenido[0];
        if (primerLote) {
          this.loteIdSeleccionado.set(primerLote.id);
        }
      },
      error: () => {},
    });
  }

  cambiarProducto(): void {
    this.productoSeleccionado.set(null);
    this.loteIdSeleccionado.set(null);
    this.productoQuery.set('');
    this.busquedaProducto$.next('');
  }

  onLoteSeleccionado(event: Event): void {
    const valor = (event.target as HTMLSelectElement).value;
    this.loteIdSeleccionado.set(valor ? Number(valor) : null);
    this.form.controls.cantidad.setValue(1);
  }

  incrementarCantidad(): void {
    const max = this.loteSeleccionado()?.stock ?? 1;
    const actual = this.form.controls.cantidad.value ?? 0;
    this.form.controls.cantidad.setValue(Math.min(max, actual + 1));
  }

  decrementarCantidad(): void {
    const actual = this.form.controls.cantidad.value ?? 0;
    this.form.controls.cantidad.setValue(Math.max(1, actual - 1));
  }

  seleccionarMotivo(motivo: MotivoMerma): void {
    this.form.controls.motivo.setValue(motivo);
  }

  pedirConfirmacion(): void {
    if (this.formularioValido()) {
      this.confirmando.set(true);
    }
  }

  confirmarMerma(): void {
    const lote = this.loteSeleccionado();
    const producto = this.productoSeleccionado();
    if (!lote || !producto) return;
    this.mermaService
      .registrarMerma({
        loteId: lote.id,
        cantidad: this.form.controls.cantidad.value ?? 0,
        motivo: this.form.controls.motivo.value,
        observacion: this.form.controls.observacion.value ?? undefined,
      })
      .subscribe({
        next: (merma) => {
          this.confirmando.set(false);
          this.form.controls.cantidad.setValue(1);
          this.form.controls.observacion.setValue('');
          this.mostrarToast(
            'success',
            `Merma registrada · ${formatearMoneda(merma.valorVenta)} dados de baja`,
          );
          // refresca los lotes del producto contra el servidor -- la cantidad topada al stock real ya la validó el servidor, esto solo trae el stock verdadero de vuelta.
          this.inventario.listarLotesDeProducto(producto.id).subscribe();
        },
        error: () => {
          this.confirmando.set(false);
          // el error ya queda en mermaService.error() (mostrado en el banner) -- acá solo cerramos el modal de confirmación.
        },
      });
  }

  private mostrarToast(variant: ToastVariant, mensaje: string): void {
    this.toastVariant.set(variant);
    this.toastMensaje.set(mensaje);
    this.toastVisible.set(false);
    setTimeout(() => this.toastVisible.set(true));
  }
}
