import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ConnStatusComponent } from '../../shared/components/conn-status/conn-status';
import { ColaVentasService } from '../../core/services/cola-ventas.service';
import { ConexionService } from '../../core/services/conexion.service';
import { CatalogoOfflineService } from '../../core/services/catalogo-offline.service';
import { ConfigService } from '../../core/services/config.service';
import { textoHaceCuanto } from '../../core/utils/fecha.util';
import { VERSION_APP } from '../../../environments/version';
import { TipoRevision } from '../../core/models/venta.model';

const ETIQUETAS_CONEXION = {
  online: 'En línea',
  sync: 'Enviando…',
  offline: 'Sin conexión',
} as const;

const TEXTO_REVISION: Record<TipoRevision, string> = {
  STOCK_INSUFICIENTE: 'stock insuficiente',
  PRECIO_DISTINTO: 'precio distinto al vigente',
  CAJA_CERRADA: 'llegó después del cierre de su caja',
  OTRO: 'observación',
};

/**
 * Tarea 22 (docs/DISENO-OFFLINE.md §7): reemplaza el botón de demostración de
 * la conexión. Muestra el estado real y cuántas ventas propias faltan enviar;
 * al tocarlo abre "Ventas sin enviar": hora, productos, método y estado de
 * cada una, SIN montos (conteo ciego, DECISIONES 2026-10-14). Las de otro
 * usuario del mismo equipo solo se cuentan.
 *
 * Arriba del panel: el catálogo guardado para vender sin conexión ("N
 * productos · actualizado hace X min", con "Actualizar ahora") y la versión
 * de la app y del servidor, para comprobar en el celular que llegó el deploy.
 */
@Component({
  selector: 'app-header-cola',
  imports: [ConnStatusComponent],
  templateUrl: './header-cola.html',
  styleUrl: './header-cola.scss',
})
export class HeaderColaComponent {
  readonly cola = inject(ColaVentasService);
  private readonly conexion = inject(ConexionService);

  readonly catalogo = inject(CatalogoOfflineService);
  private readonly config = inject(ConfigService);

  readonly estado = this.conexion.estado;
  readonly enLinea = this.conexion.enLinea;
  readonly abierto = signal(false);

  /** Reloj del "hace X min": se mueve cada 30 s mientras el encabezado existe. */
  private readonly ahora = signal(Date.now());

  readonly textoCatalogo = computed(() => {
    const n = this.catalogo.cantidadProductos();
    const generado = this.catalogo.generadoEn();
    if (n === null || generado === null) return 'todavía no se descargó en este equipo';
    return `${n} ${n === 1 ? 'producto' : 'productos'} · actualizado ${textoHaceCuanto(generado, this.ahora())}`;
  });

  readonly versionApp = corta(VERSION_APP);
  readonly versionServidor = computed(() => corta(this.config.config()?.version ?? null));

  constructor() {
    const reloj = setInterval(() => this.ahora.set(Date.now()), 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(reloj));
  }

  readonly etiqueta = computed(() => {
    const base = ETIQUETAS_CONEXION[this.estado()];
    const n = this.cola.cantidadPropias();
    return n > 0 ? `${base} · ${n} sin enviar` : base;
  });

  readonly hayAlgo = computed(
    () => this.cola.cantidadPropias() > 0 || this.cola.deOtrosUsuarios().length > 0 || this.cola.conObservaciones().length > 0,
  );

  alternar(): void {
    this.ahora.set(Date.now());
    this.abierto.update((v) => !v);
  }

  actualizarCatalogo(): void {
    this.catalogo.descargar().subscribe({ error: () => {} }); // el error queda en catalogo.error()
  }

  enviarAhora(): void {
    void this.conexion.comprobar().then(() => this.cola.enviarPendientes());
  }

  hora(iso: string): string {
    return new Date(iso).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' });
  }

  metodo(m: string): string {
    return m === 'efectivo' ? 'Efectivo' : m === 'yape' ? 'Yape' : 'Tarjeta';
  }

  textoRevision(tipo: TipoRevision): string {
    return TEXTO_REVISION[tipo];
  }
}

/** Commit corto (7) o el texto tal cual ("local"); sin dato, "—". */
function corta(version: string | null): string {
  if (!version) return '—';
  return /^[0-9a-f]{40}$/.test(version) ? version.slice(0, 7) : version;
}
