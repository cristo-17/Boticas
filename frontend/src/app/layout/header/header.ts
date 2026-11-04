import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonComponent } from '../../shared/components/button/button';
import { HeaderNotificacionesComponent } from './header-notificaciones';
import { HeaderColaComponent } from './header-cola';
import { AuthService } from '../../core/services/auth.service';
import { textoSede } from '../../core/utils/sede.util';

@Component({
  selector: 'app-header',
  imports: [ButtonComponent, HeaderColaComponent, HeaderNotificacionesComponent],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class HeaderComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly usuario = this.auth.usuarioActual;

  // boticaNombre/boticaDireccion vienen crudos del backend (Tarea 12); sin dirección queda solo el nombre (textoSede).
  readonly sede = computed(() => {
    const u = this.usuario();
    return u ? textoSede(u.boticaNombre, u.boticaDireccion) : 'Sin sesión iniciada';
  });

  cerrarSesion(): void {
    this.auth.cerrarSesion().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
