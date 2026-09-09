import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners, isDevMode } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { firstValueFrom, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { routes } from './app.routes';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { ConfigService } from './core/services/config.service';
import { provideServiceWorker } from '@angular/service-worker';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([errorInterceptor])),
    // GET /api/config una sola vez al iniciar la app, antes de que
    // arranque cualquier pantalla (Tarea 9) — no en cada componente.
    // catchError es obligatorio acá: un provideAppInitializer que falla
    // aborta el bootstrap ENTERO de Angular (pantalla en blanco, sin
    // login, sin nada) -- verificado apagando el backend a propósito.
    // Cada servicio que lee config() ya tiene su propio valor por
    // defecto para este caso (ver "_DEFECTO" en cada uno).
    provideAppInitializer(() =>
      firstValueFrom(inject(ConfigService).cargar().pipe(catchError(() => of(undefined)))),
    ),
    // Service worker solo en build de producción (Tarea 10) — registro
    // diferido 30s tras estabilizar la app para no competir con la carga
    // inicial (GET /api/config, primera pantalla).
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ]
};
