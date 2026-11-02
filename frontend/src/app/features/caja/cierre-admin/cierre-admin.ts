import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner';
import { PaginacionComponent } from '../../../shared/components/paginacion/paginacion';
import { ToastComponent, ToastVariant } from '../../../shared/components/toast/toast';
import { ErrorTraducido } from '../../../core/interceptors/error.interceptor';
import { CajaDiaria } from '../../../core/models/caja.model';
import { CajaService } from '../../../core/services/caja.service';
import { ConteoCierre, ConteoCierreComponent } from '../conteo-cierre/conteo-cierre';
import { EgresoCajaComponent } from '../egreso-caja/egreso-caja';
import { ResultadoCierreComponent } from '../resultado-cierre/resultado-cierre';

const TAMANO_PAGINA = 20;

/**
 * D-E2-1: el ADMINISTRADOR elige una caja abierta de su botica (la de un
 * técnico que se fue, una abandonada) y la cierra con conteo ciego. Antes
 * del POST no se muestra ningún monto de la caja: solo quién, turno y hora.
 */
@Component({
  selector: 'app-cierre-admin',
  imports: [
    ConteoCierreComponent,
    EgresoCajaComponent,
    ErrorBannerComponent,
    PaginacionComponent,
    ResultadoCierreComponent,
    ToastComponent,
  ],
  templateUrl: './cierre-admin.html',
  styleUrl: './cierre-admin.scss',
})
export class CierreAdminComponent implements OnInit {
  private readonly caja = inject(CajaService);
  private readonly conteo = viewChild(ConteoCierreComponent);

  readonly abiertas = this.caja.abiertas;
  readonly cargando = this.caja.cargando;
  readonly error = this.caja.error;
  readonly ultimoCierre = this.caja.ultimoCierre;

  /** Estado local de la pantalla (no viene del servidor): la caja que el admin va a contar. */
  readonly seleccionada = signal<CajaDiaria | null>(null);
  /** Se muestra el resultado solo del cierre hecho en esta pantalla. */
  readonly mostrarResultado = signal(false);

  readonly toastVisible = signal(false);
  readonly toastMensaje = signal('');
  readonly toastVariant = signal<ToastVariant>('success');

  ngOnInit(): void {
    this.irAPagina(0);
  }

  irAPagina(pagina: number): void {
    this.caja.listarAbiertas(pagina, TAMANO_PAGINA).subscribe({ error: () => {} });
  }

  elegir(caja: CajaDiaria): void {
    this.seleccionada.set(caja);
    this.mostrarResultado.set(false);
  }

  volverALista(): void {
    this.seleccionada.set(null);
  }

  onGastoRegistrado(): void {
    this.mostrarToast('success', 'Gasto registrado');
  }

  // POST /api/caja/cerrar con cajaId (solo ADMINISTRADOR cierra una caja ajena).
  cerrar(conteo: ConteoCierre): void {
    const caja = this.seleccionada();
    if (!caja) return;
    this.caja.cerrarCaja({ ...conteo, cajaId: caja.id }).subscribe({
      next: () => {
        this.conteo()?.terminar(true);
        this.seleccionada.set(null);
        this.mostrarResultado.set(true);
        this.mostrarToast('success', `Caja de ${caja.usuarioNombre} cerrada`);
      },
      error: (err: HttpErrorResponse & { traducido?: ErrorTraducido }) => {
        this.conteo()?.terminar(false);
        this.mostrarToast('error', err.traducido?.mensaje ?? 'No se pudo cerrar la caja. Reintenta.');
      },
    });
  }

  horaTexto(iso: string): string {
    return new Date(iso).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' });
  }

  private mostrarToast(variant: ToastVariant, mensaje: string): void {
    this.toastVariant.set(variant);
    this.toastMensaje.set(mensaje);
    this.toastVisible.set(false);
    setTimeout(() => this.toastVisible.set(true));
  }
}
