import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { BadgeComponent } from '../../shared/components/badge/badge';
import { ButtonComponent } from '../../shared/components/button/button';
import { CardComponent } from '../../shared/components/card/card';
import { ChipComponent } from '../../shared/components/chip/chip';
import { ModalComponent } from '../../shared/components/modal/modal';
import { TableComponent } from '../../shared/components/table/table';
import { ToastComponent, ToastVariant } from '../../shared/components/toast/toast';
import { MotivoMerma } from '../../core/models/merma.model';
import { AuthService } from '../../core/services/auth.service';
import { InventarioService } from '../../core/services/inventario.service';
import { MermaService } from '../../core/services/merma.service';
import { ProductoService } from '../../core/services/producto.service';
import { formatearMoneda } from '../../core/utils/moneda.util';
import { toSignal } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-merma',
  imports: [
    ReactiveFormsModule,
    BadgeComponent,
    ButtonComponent,
    CardComponent,
    ChipComponent,
    ModalComponent,
    TableComponent,
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

  readonly motivos = this.mermaService.motivosMerma;
  readonly formatearMoneda = formatearMoneda;
  readonly registrando = this.mermaService.cargando;
  readonly mermas = this.mermaService.mermas;

  /**
   * productoId/loteId son signals de number, no FormControl: app-field
   * (select) es string-only porque un <select> nativo del DOM solo
   * puede devolver string. La conversión ocurre UNA sola vez, en
   * onProductoSeleccionado/onLoteSeleccionado (CLAUDE.md, "Los ids que
   * vienen del backend son number") — de ahí para adentro, todo id es
   * number, sin más conversiones.
   */
  readonly productoIdSeleccionado = signal<number | null>(null);
  readonly loteIdSeleccionado = signal<number | null>(null);

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

  // Todos los lotes disponibles en el inventario, para filtrar por producto y lote. Se actualiza automáticamente cuando InventarioService.lotes() cambia.
  private readonly todosLosLotes = this.inventario.lotes;

  private readonly formValue = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  readonly productosDisponibles = computed(() => {
    const vistos = new Map<number, string>();
    for (const lote of this.todosLosLotes()) {
      if (!vistos.has(lote.productoId)) {
        vistos.set(lote.productoId, this.productoService.obtenerPorId(lote.productoId)?.nombre ?? lote.productoNombre);
      }
    }
    return Array.from(vistos, ([productoId, nombre]) => ({ productoId, nombre }));
  });

  readonly productoOpciones = computed(() =>
    this.productosDisponibles().map((p) => ({ value: p.productoId, label: p.nombre })),
  );

  readonly lotesDelProducto = computed(() => {
    const productoId = this.productoIdSeleccionado();
    return this.todosLosLotes().filter((l) => l.productoId === productoId);
  });

  readonly loteOpciones = computed(() =>
    this.lotesDelProducto().map((l) => ({ value: l.id, label: l.codigo })),
  );

  readonly loteSeleccionado = computed(() => {
    const loteId = this.loteIdSeleccionado();
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
    this.inventario.listarLotes({}, 0, 100).subscribe(() => {
      const primerProducto = this.productosDisponibles()[0];
      if (primerProducto) {
        this.seleccionarProducto(primerProducto.productoId);
      }
    });

    this.mermaService.listarMermasDelDia().subscribe({ error: () => {} });
  }

  formatearHora(iso: string): string {
    const d = new Date(iso);
    return isNaN(d.getTime())
      ? iso
      : d.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' });
  }

  private seleccionarProducto(productoId: number): void {
    this.productoIdSeleccionado.set(productoId);
    const primerLote = this.todosLosLotes().find((l) => l.productoId === productoId) ?? null;
    this.loteIdSeleccionado.set(primerLote?.id ?? null);
    this.form.controls.cantidad.setValue(1);
  }

  onProductoSeleccionado(event: Event): void {
    const valor = (event.target as HTMLSelectElement).value;
    if (valor) {
      this.seleccionarProducto(Number(valor));
    }
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
            `Merma registrada · ${formatearMoneda(merma.valorVenta)} dados de baja`,
          );
          // Recarga lotes para reflejar el stock actualizado por el backend.
          this.inventario.listarLotes({}, 0, 100).subscribe({ error: () => {} });
        },
        error: () => {
          this.confirmando.set(false);
          this.mostrarToast('error', this.mermaService.error() ?? 'No se pudo registrar la merma.');
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
