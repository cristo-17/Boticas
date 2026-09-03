import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from '../header/header';
import { SidebarNavComponent } from '../sidebar-nav/sidebar-nav';
import { BottomNavComponent } from '../bottom-nav/bottom-nav';
import { ConnStatusComponent } from '../../shared/components/conn-status/conn-status';
import { AlertaService } from '../../core/services/alerta.service';
import { ConexionService } from '../../core/services/conexion.service';

const MENSAJE_BANNER = {
  sync: 'Sincronizando ventas guardadas localmente…',
  offline:
    'Sin conexión. Puedes seguir vendiendo: todo se guarda en el equipo y se sube al reconectar.',
} as const;

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    HeaderComponent,
    SidebarNavComponent,
    BottomNavComponent,
    ConnStatusComponent,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly conexion = inject(ConexionService);
  private readonly alertaService = inject(AlertaService);

  readonly estadoConexion = this.conexion.estado;

  constructor() {
    this.alertaService.listarAlertas().subscribe();
  }

  mensajeBanner(): string {
    const estado = this.estadoConexion();
    return estado === 'online' ? '' : MENSAJE_BANNER[estado];
  }
}
