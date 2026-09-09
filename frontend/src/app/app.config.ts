import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners, isDevMode } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { firstValueFrom, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { AuthService } from './core/services/auth.service';
import { ConfigService } from './core/services/config.service';
import { provideServiceWorker } from '@angular/service-worker';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    // Carga configuración inicial y restaura sesión si existe token guardado
    provideAppInitializer(() => {
      const config = inject(ConfigService);
      const auth = inject(AuthService);
      return Promise.all([
        firstValueFrom(config.cargar().pipe(catchError(() => of(undefined)))),
        firstValueFrom(auth.obtenerUsuarioActual().pipe(catchError(() => of(null)))),
      ]);
    }),
    // Service worker solo en build de producción (Tarea 10) — registro
    // diferido 30s tras estabilizar la app para no competir con la carga
    // inicial (GET /api/config, primera pantalla).
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ]
};
