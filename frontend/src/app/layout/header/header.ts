import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonComponent } from '../../shared/components/button/button';
import { ConnStatusComponent } from '../../shared/components/conn-status/conn-status';
import { HeaderNotificacionesComponent } from './header-notificaciones';
import { AuthService } from '../../core/services/auth.service';
import { ConexionService } from '../../core/services/conexion.service';
import { textoSede } from '../../core/utils/sede.util';

const ETIQUETAS_CONEXION = {
  online: 'En línea',
  sync: 'Sincronizando',
  offline: 'Sin conexión',
} as const;

@Component({
  selector: 'app-header',
  imports: [ButtonComponent, ConnStatusComponent, HeaderNotificacionesComponent],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class HeaderComponent {
  private readonly auth = inject(AuthService);
  private readonly conexion = inject(ConexionService);
  private readonly router = inject(Router);

  readonly usuario = this.auth.usuarioActual;
  readonly estadoConexion = this.conexion.estado;
  readonly etiquetaConexion = computed(() => ETIQUETAS_CONEXION[this.estadoConexion()]);

  // boticaNombre/boticaDireccion vienen crudos del backend (Tarea 12); sin dirección queda solo el nombre (textoSede).
  readonly sede = computed(() => {
    const u = this.usuario();
    return u ? textoSede(u.boticaNombre, u.boticaDireccion) : 'Sin sesión iniciada';
  });

  alternarConexion(): void {
    this.conexion.alternar();
  }

  cerrarSesion(): void {
    this.auth.cerrarSesion().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
