import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
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
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
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
    // GET /api/auth/yo una sola vez al iniciar (Tarea 12): rehidrata la sesión si había un
    // token guardado. Mismo catchError obligatorio que arriba -- un token vencido no debe
    // tumbar el arranque de la app, solo dejarla sin sesión (el guard ya manda a /login).
    provideAppInitializer(() =>
      firstValueFrom(inject(AuthService).obtenerUsuarioActual().pipe(catchError(() => of(null)))),
    ),
    // Service worker solo en build de producción (Tarea 10) — registro
    // diferido 30s tras estabilizar la app para no competir con la carga
    // inicial (GET /api/config, primera pantalla).
    // environment.production, NO isDevMode(): verificado (docs/BITACORA.md
    // [FE-009]) que isDevMode() sigue devolviendo true incluso en un build
    // de producción real de este proyecto (no se llama enableProdMode()) --
    // con !isDevMode() el service worker nunca se habría activado en Vercel.
    provideServiceWorker('ngsw-worker.js', {
      enabled: environment.production,
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ]
};
