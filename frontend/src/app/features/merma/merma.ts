import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonComponent } from '../../shared/components/button/button';
import { CardComponent } from '../../shared/components/card/card';
import { ChipComponent } from '../../shared/components/chip/chip';
import { FieldComponent } from '../../shared/components/field/field';
import { ModalComponent } from '../../shared/components/modal/modal';
import { ToastComponent, ToastVariant } from '../../shared/components/toast/toast';
import { MotivoMerma } from '../../core/models/merma.model';
import { AuthService } from '../../core/services/auth.service';
import { InventarioService } from '../../core/services/inventario.service';
import { MermaService } from '../../core/services/merma.service';
import { ProductoService } from '../../core/services/producto.service';
import { formatearMoneda } from '../../core/utils/moneda.util';

@Component({
  selector: 'app-merma',
  imports: [
    ReactiveFormsModule,
    ButtonComponent,
    CardComponent,
    ChipComponent,
    FieldComponent,
    ModalComponent,
    ToastComponent,
  ],
  templateUrl: './merma.html',
  styleUrl: './merma.scss',
})
export class MermaScreen implements OnInit {
  private readonly inventario = inject(InventarioService);
  private readonly productoService = inject(ProductoService);
  private readonly mermaService = inject(MermaService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly motivos = this.mermaService.motivosMerma;
  readonly formatearMoneda = formatearMoneda;
  readonly registrando = this.mermaService.cargando;

  // Formulario reactivo para registrar una merma. Se conecta a app-field (ControlValueAccessor) en la plantilla.
  readonly form = new FormGroup({
    productoId: new FormControl<string | null>(null, Validators.required),
    loteId: new FormControl<string | null>(null, Validators.required),
    cantidad: new FormControl(1, [Validators.required, Validators.min(1)]),
    motivo: new FormControl<MotivoMerma>('Vencimiento', {
      nonNullable: true,
      validators: Validators.required,
    }),
    observacion: new FormControl(''),
  });

  readonly confirmando = signal(false);

  // Todos los lotes disponibles en el inventario, para filtrar por producto y lote. Se actualiza automáticamente cuando InventarioService.lotes() cambia.
  private readonly todosLosLotes = this.inventario.lotes;

  private readonly formValue = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  readonly productosDisponibles = computed(() => {
    const vistos = new Map<string, string>();
    for (const lote of this.todosLosLotes()) {
      if (!vistos.has(lote.productoId)) {
        vistos.set(
          lote.productoId,
          this.productoService.obtenerPorId(lote.productoId)?.nombre ?? lote.productoNombre,
        );
      }
    }
    return Array.from(vistos, ([productoId, nombre]) => ({ productoId, nombre }));
  });

  readonly productoOpciones = computed(() =>
    this.productosDisponibles().map((p) => ({ value: p.productoId, label: p.nombre })),
  );

  readonly lotesDelProducto = computed(() => {
    const productoId = this.formValue().productoId;
    return this.todosLosLotes().filter((l) => l.productoId === productoId);
  });

  readonly loteOpciones = computed(() =>
    this.lotesDelProducto().map((l) => ({ value: l.id, label: l.codigo })),
  );

  readonly loteSeleccionado = computed(() => {
    const loteId = this.formValue().loteId;
    return this.lotesDelProducto().find((l) => l.id === loteId) ?? null;
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
    this.form.controls.productoId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((productoId) => {
        if (!productoId) return;
        const primerLote = this.todosLosLotes().find((l) => l.productoId === productoId) ?? null;
        this.form.controls.loteId.setValue(primerLote?.id ?? null);
      });
    this.form.controls.loteId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.form.controls.cantidad.setValue(1);
      });

    this.inventario.listarLotes({}).subscribe(() => {
      const primerProducto = this.productosDisponibles()[0];
      if (primerProducto) {
        this.form.controls.productoId.setValue(primerProducto.productoId);
      }
    });
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
    if (!lote) return;
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
            `Merma registrada · ${formatearMoneda(merma.valor)} dados de baja`,
          );
          // refresca el lote (el servicio ya descontó el stock en InventarioService)
          this.inventario.listarLotes({}).subscribe();
        },
        error: () => {
          this.confirmando.set(false);
          this.mostrarToast('error', 'No se pudo registrar la merma. Reintenta.');
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
