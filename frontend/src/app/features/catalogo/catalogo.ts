import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { BadgeComponent } from '../../shared/components/badge/badge';
import { ButtonComponent } from '../../shared/components/button/button';
import { CardComponent } from '../../shared/components/card/card';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner';
import { PaginacionComponent } from '../../shared/components/paginacion/paginacion';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton';
import { TableComponent } from '../../shared/components/table/table';
import { PresentacionCatalogo, ProductoCatalogo, ProductoRequest } from '../../core/models/catalogo.model';
import { CatalogoService } from '../../core/services/catalogo.service';
import { formatearMoneda } from '../../core/utils/moneda.util';

const TAMANO_PAGINA = 20;

/**
 * Fila del formulario de presentaciones. factor/precio son number | null
 * desde el borde (regla 3: el <input type="number"> da string, se convierte
 * una sola vez en onNumero). esUnidad = la fila de factor 1: siempre está,
 * su factor no se edita y no se puede quitar (regla de dominio del servidor).
 */
interface FilaPresentacion {
  clave: number;
  id?: number;
  etiqueta: string;
  factor: number | null;
  precio: number | null;
  esUnidad: boolean;
}

let siguienteClave = 1;

function filaUnidad(): FilaPresentacion {
  return { clave: siguienteClave++, etiqueta: 'Unidad', factor: 1, precio: null, esUnidad: true };
}

function filaDesde(p: PresentacionCatalogo): FilaPresentacion {
  return { clave: siguienteClave++, id: p.id, etiqueta: p.etiqueta, factor: p.factorConversion, precio: p.precio, esUnidad: p.factorConversion === 1 };
}

/** Catálogo de productos, solo ADMINISTRADOR (Tarea 17, Bloque A). Ruta con rolGuard; el servidor exige el rol igual. */
@Component({
  selector: 'app-catalogo',
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
  templateUrl: './catalogo.html',
  styleUrl: './catalogo.scss',
})
export class CatalogoScreen implements OnInit {
  private readonly catalogo = inject(CatalogoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly productos = this.catalogo.productos;
  readonly pagina = this.catalogo.pagina;
  readonly cargando = this.catalogo.cargando;
  readonly error = this.catalogo.error;
  readonly guardando = this.catalogo.guardando;
  readonly errorGuardado = this.catalogo.errorGuardado;
  readonly formatearMoneda = formatearMoneda;

  // --- Listado: toda carga pasa por switchMap (regla 4, [FE-008]); la búsqueda además con debounce ---
  readonly busqueda = signal('');
  private readonly paginaActual = signal(0);
  private readonly busqueda$ = new Subject<string>();
  private readonly cargas$ = new Subject<void>();

  readonly vacio = computed(() => !this.cargando() && !this.error() && this.productos().length === 0);

  constructor() {
    this.cargas$
      .pipe(
        switchMap(() => this.catalogo.listar(this.busqueda().trim(), this.paginaActual(), TAMANO_PAGINA)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ error: () => {} }); // el error ya queda en catalogo.error()
    this.busqueda$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.paginaActual.set(0);
        this.cargas$.next();
      });
  }

  ngOnInit(): void {
    this.cargas$.next();
  }

  onBusquedaInput(event: Event): void {
    const valor = (event.target as HTMLInputElement).value;
    this.busqueda.set(valor);
    this.busqueda$.next(valor.trim());
  }

  irAPagina(pagina: number): void {
    this.paginaActual.set(Math.max(0, pagina));
    this.cargas$.next();
  }

  recargar(): void {
    this.cargas$.next();
  }

  presentacionesActivas(p: ProductoCatalogo): PresentacionCatalogo[] {
    return p.presentaciones.filter((x) => x.activo);
  }

  textoPresentacion(x: PresentacionCatalogo, unidadNombre: string): string {
    return x.factorConversion === 1 ? x.etiqueta : `${x.etiqueta} ×${x.factorConversion} ${unidadNombre}`;
  }

  // --- Activar / desactivar (reversible: sin confirmación; el servidor lo valida) ---
  readonly productoEnCambio = signal<number | null>(null);

  alternarEstado(p: ProductoCatalogo): void {
    this.productoEnCambio.set(p.id);
    this.catalogo.cambiarEstado(p.id, !p.activo).subscribe({
      next: () => this.productoEnCambio.set(null),
      error: () => this.productoEnCambio.set(null), // el mensaje queda en errorGuardado (banner de la pantalla)
    });
  }

  // --- Formulario de alta / edición (signals locales: es estado de la pantalla, no del servidor) ---
  readonly formularioAbierto = signal(false);
  readonly editandoId = signal<number | null>(null);
  readonly nombre = signal('');
  readonly laboratorio = signal('');
  readonly categoria = signal('');
  readonly codigoBarras = signal('');
  readonly unidadNombre = signal('');
  readonly filas = signal<FilaPresentacion[]>([]);
  /** Presentaciones desactivadas del producto en edición: se pueden reactivar. */
  readonly desactivadas = signal<PresentacionCatalogo[]>([]);
  /** Existentes quitadas en esta edición: el servidor las desactiva al guardar. */
  readonly quitadas = signal<string[]>([]);
  /** La lista de faltantes se muestra recién tras el primer intento de guardar, no al abrir un formulario vacío. */
  readonly intentoGuardar = signal(false);

  readonly erroresFormulario = computed<string[]>(() => {
    const errores: string[] = [];
    if (!this.nombre().trim()) errores.push('Falta el nombre.');
    if (!this.categoria().trim()) errores.push('Falta la categoría.');
    if (!this.codigoBarras().trim()) errores.push('Falta el código de barras.');
    if (!this.unidadNombre().trim()) errores.push('Falta la unidad (tableta, cápsula, frasco…).');
    const etiquetas = new Set<string>();
    for (const f of this.filas()) {
      const etiqueta = f.etiqueta.trim().toLowerCase();
      if (!etiqueta) errores.push('Toda presentación necesita un nombre.');
      else if (etiquetas.has(etiqueta)) errores.push(`La presentación "${f.etiqueta.trim()}" está repetida.`);
      etiquetas.add(etiqueta);
      if (!f.esUnidad && (f.factor === null || !Number.isInteger(f.factor) || f.factor < 2)) {
        errores.push(`"${f.etiqueta.trim() || 'Presentación'}": el factor debe ser un entero mayor que 1.`);
      }
      if (f.precio === null || f.precio <= 0) {
        errores.push(`"${f.etiqueta.trim() || 'Presentación'}": el precio debe ser mayor que 0.`);
      }
    }
    return errores;
  });

  abrirNuevo(): void {
    this.catalogo.limpiarErrorGuardado();
    this.editandoId.set(null);
    this.nombre.set('');
    this.laboratorio.set('');
    this.categoria.set('');
    this.codigoBarras.set('');
    this.unidadNombre.set('');
    this.filas.set([filaUnidad()]);
    this.desactivadas.set([]);
    this.quitadas.set([]);
    this.intentoGuardar.set(false);
    this.formularioAbierto.set(true);
  }

  abrirEdicion(p: ProductoCatalogo): void {
    this.catalogo.limpiarErrorGuardado();
    this.editandoId.set(p.id);
    this.nombre.set(p.nombre);
    this.laboratorio.set(p.laboratorio ?? '');
    this.categoria.set(p.categoria);
    this.codigoBarras.set(p.codigoBarras);
    this.unidadNombre.set(p.unidadNombre);
    const activas = p.presentaciones.filter((x) => x.activo).map(filaDesde);
    // Un producto viejo sin fila de factor 1 la gana al editar (el servidor la exige).
    this.filas.set(activas.some((f) => f.esUnidad) ? activas : [...activas, filaUnidad()]);
    this.desactivadas.set(p.presentaciones.filter((x) => !x.activo));
    this.quitadas.set([]);
    this.intentoGuardar.set(false);
    this.formularioAbierto.set(true);
  }

  cerrarFormulario(): void {
    this.formularioAbierto.set(false);
  }

  agregarFila(): void {
    this.filas.update((filas) => [
      ...filas.filter((f) => !f.esUnidad),
      { clave: siguienteClave++, etiqueta: '', factor: null, precio: null, esUnidad: false },
      ...filas.filter((f) => f.esUnidad),
    ]);
  }

  quitarFila(fila: FilaPresentacion): void {
    if (fila.esUnidad) return;
    this.filas.update((filas) => filas.filter((f) => f.clave !== fila.clave));
    if (fila.id !== undefined) {
      this.quitadas.update((q) => [...q, fila.etiqueta]);
    }
  }

  reactivar(p: PresentacionCatalogo): void {
    this.desactivadas.update((d) => d.filter((x) => x.id !== p.id));
    this.filas.update((filas) => [...filas.filter((f) => !f.esUnidad), filaDesde(p), ...filas.filter((f) => f.esUnidad)]);
  }

  onEtiqueta(fila: FilaPresentacion, event: Event): void {
    const valor = (event.target as HTMLInputElement).value;
    this.filas.update((filas) => filas.map((f) => (f.clave === fila.clave ? { ...f, etiqueta: valor } : f)));
  }

  /** Borde de entrada (regla 3): string del input → number | null, una sola vez. */
  onNumero(fila: FilaPresentacion, campo: 'factor' | 'precio', event: Event): void {
    const texto = (event.target as HTMLInputElement).value;
    const valor = texto === '' ? null : Number(texto);
    this.filas.update((filas) =>
      filas.map((f) => (f.clave === fila.clave ? { ...f, [campo]: valor !== null && Number.isNaN(valor) ? null : valor } : f)),
    );
  }

  guardar(): void {
    if (this.erroresFormulario().length > 0) {
      this.intentoGuardar.set(true);
      return;
    }
    const request: ProductoRequest = {
      nombre: this.nombre().trim(),
      laboratorio: this.laboratorio().trim() || null,
      categoria: this.categoria().trim(),
      codigoBarras: this.codigoBarras().trim(),
      unidadNombre: this.unidadNombre().trim(),
      presentaciones: this.filas().map((f) => ({
        ...(f.id !== undefined ? { id: f.id } : {}),
        etiqueta: f.etiqueta.trim(),
        factorConversion: f.factor as number,
        precio: f.precio as number,
      })),
    };
    const id = this.editandoId();
    const peticion = id === null ? this.catalogo.crear(request) : this.catalogo.actualizar(id, request);
    peticion.subscribe({
      next: () => {
        this.formularioAbierto.set(false);
        if (id === null) this.recargar(); // uno nuevo puede caer en cualquier página del orden alfabético
      },
      error: () => {}, // el mensaje del servidor queda en errorGuardado, dentro del formulario
    });
  }
}
