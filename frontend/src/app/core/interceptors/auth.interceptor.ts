import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.obtenerToken();

  let peticion = req;
  if (token && !req.url.includes('/auth/login')) {
    peticion = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });
  }

  return next(peticion).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !req.url.includes('/auth/login') && !req.url.includes('/auth/logout')) {
        auth.limpiarSesion();
        router.navigateByUrl('/login');
      }
      return throwError(() => err);
    }),
  );
};
