import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Protege el Shell completo (Tarea 12) — sin sesión, redirige a /login en vez de dejar pasar. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.estaAutenticado()) {
    return true;
  }
  return inject(Router).parseUrl('/login');
};
