import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { ButtonComponent } from '../../shared/components/button/button';
import { AlertaService } from '../../core/services/alerta.service';
import { AuthService } from '../../core/services/auth.service';
import { CajaService } from '../../core/services/caja.service';
import { ConexionService } from '../../core/services/conexion.service';
import { NAV_ITEMS } from '../nav-items';

const ETIQUETAS_CONEXION = {
  online: 'En línea',
  sync: 'Sincronizando',
  offline: 'Sin conexión',
} as const;

@Component({
  selector: 'app-sidebar-nav',
  imports: [RouterLink, RouterLinkActive, ButtonComponent],
  templateUrl: './sidebar-nav.html',
  styleUrl: './sidebar-nav.scss',
})
export class SidebarNavComponent {
  private readonly auth = inject(AuthService);
  private readonly caja = inject(CajaService);
  private readonly conexion = inject(ConexionService);
  private readonly alertaService = inject(AlertaService);
  private readonly router = inject(Router);

  readonly navItems = NAV_ITEMS;
  readonly usuario = this.auth.usuarioActual;
  readonly cajaActual = this.caja.cajaActual;
  readonly estadoConexion = this.conexion.estado;
  readonly etiquetaConexion = computed(() => ETIQUETAS_CONEXION[this.estadoConexion()]);
  readonly cantidadAlertas = computed(() => this.alertaService.alertas().length);

  readonly iniciales = computed(() => {
    const nombre = this.usuario()?.nombre ?? '';
    return nombre
      .split(' ')
      .map((parte) => parte[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();
  });

  readonly horaApertura = computed(() => {
    const hora = this.cajaActual()?.horaApertura;
    if (!hora) return null;
    return new Date(hora).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' });
  });

  cerrarSesion(): void {
    this.auth.cerrarSesion().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
