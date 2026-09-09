import {
  Component,
  ElementRef,
  HostListener,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { NotificacionService } from '../../core/services/notificacion.service';
import { Notificacion, TipoNotificacion } from '../../core/models/notificacion.model';

@Component({
  selector: 'app-header-notificaciones',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './header-notificaciones.html',
  styleUrl: './header-notificaciones.scss',
})
export class HeaderNotificacionesComponent implements OnInit {
  private readonly notifService = inject(NotificacionService);
  private readonly router = inject(Router);
  private readonly elementRef = inject(ElementRef);

  readonly abierto = signal<boolean>(false);
  readonly notificaciones = this.notifService.notificaciones;
  readonly noLeidasCount = this.notifService.noLeidasCount;
  readonly conectadoSse = this.notifService.conectadoSse;
  readonly permisoNativo = this.notifService.permisoNativo;
  readonly alertaEnVivo = this.notifService.alertaEnVivo;

  readonly tieneNoLeidas = computed(() => this.noLeidasCount() > 0);

  ngOnInit(): void {
    this.notifService.conectar();
    this.notifService.cargarHistorial();
  }

  toggle(): void {
    this.abierto.update((v) => !v);
  }

  cerrar(): void {
    this.abierto.set(false);
  }

  marcarLeida(notificacion: Notificacion, event?: MouseEvent): void {
    event?.stopPropagation();
    if (!notificacion.leido) {
      this.notifService.marcarLeida(notificacion.id);
    }
  }

  marcarTodas(): void {
    this.notifService.marcarTodasLeidas();
  }

  solicitarPermiso(): void {
    this.notifService.solicitarPermisoNativo();
  }

  verTodas(): void {
    this.cerrar();
    this.router.navigate(['/alertas']);
  }

  formatearHora(fechaIso: string): string {
    if (!fechaIso) return '';
    const fecha = new Date(fechaIso);
    if (isNaN(fecha.getTime())) return '';

    const ahora = new Date();
    const diffMs = ahora.getTime() - fecha.getTime();
    const diffMin = Math.floor(diffMs / 60000);

    if (diffMin < 1) return 'Hace un momento';
    if (diffMin < 60) return `Hace ${diffMin} min`;
    const diffHoras = Math.floor(diffMin / 60);
    if (diffHoras < 24) return `Hace ${diffHoras} h`;

    return fecha.toLocaleDateString('es-PE', {
      day: '2-digit',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  iconoTipo(tipo: TipoNotificacion): string {
    switch (tipo) {
      case 'DESCUADRE_GRAVE':
        return 'bi-exclamation-octagon-fill';
      case 'STOCK_CRITICO':
        return 'bi-box-seam-fill';
      case 'LOTE_VENCIDO':
        return 'bi-calendar-x-fill';
      default:
        return 'bi-info-circle-fill';
    }
  }

  claseTipo(tipo: TipoNotificacion): string {
    switch (tipo) {
      case 'DESCUADRE_GRAVE':
        return 'notif-item--peligro';
      case 'STOCK_CRITICO':
        return 'notif-item--alerta';
      case 'LOTE_VENCIDO':
        return 'notif-item--advertencia';
      default:
        return 'notif-item--info';
    }
  }

  @HostListener('document:click', ['$event'])
  onClickOutside(event: MouseEvent): void {
    if (this.abierto() && !this.elementRef.nativeElement.contains(event.target)) {
      this.cerrar();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.abierto()) {
      this.cerrar();
    }
  }
}
