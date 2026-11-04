import { Component, computed, inject, signal } from '@angular/core';
import { ConnStatusComponent } from '../../shared/components/conn-status/conn-status';
import { ColaVentasService } from '../../core/services/cola-ventas.service';
import { ConexionService } from '../../core/services/conexion.service';
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

  readonly estado = this.conexion.estado;
  readonly abierto = signal(false);

  readonly etiqueta = computed(() => {
    const base = ETIQUETAS_CONEXION[this.estado()];
    const n = this.cola.cantidadPropias();
    return n > 0 ? `${base} · ${n} sin enviar` : base;
  });

  readonly hayAlgo = computed(
    () => this.cola.cantidadPropias() > 0 || this.cola.deOtrosUsuarios().length > 0 || this.cola.conObservaciones().length > 0,
  );

  alternar(): void {
    this.abierto.update((v) => !v);
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
