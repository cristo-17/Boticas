import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Rol } from '../models/usuario.model';
import { AuthService } from '../services/auth.service';

/**
 * R17: una pantalla de un solo rol tiene guard de rol, no solo de sesión.
 * Sin el rol, vuelve al Dashboard (no a /login: la sesión es válida). El
 * servidor igual responde 403; esto solo evita mostrar una pantalla vacía.
 */
export function rolGuard(...roles: Rol[]): CanActivateFn {
  return () => {
    const rol = inject(AuthService).usuarioActual()?.rol;
    return rol !== undefined && roles.includes(rol) ? true : inject(Router).parseUrl('/alertas');
  };
}
