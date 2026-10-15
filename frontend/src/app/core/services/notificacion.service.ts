import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, defer, merge, throwError, timer } from 'rxjs';
import { catchError, distinctUntilChanged, finalize, ignoreElements, map, retry, switchMap, tap } from 'rxjs/operators';
import { Notificacion, TicketSse } from '../models/notificacion.model';
import { AuthService } from './auth.service';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/notificaciones`;
const REINTENTO_MS = 5000;

@Injectable({ providedIn: 'root' })
export class NotificacionService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private readonly _notificaciones = signal<Notificacion[]>([]);
  readonly notificaciones = this._notificaciones.asReadonly();

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  /** Regla de frontend (CLAUDE.md): toda pantalla que llama a este servicio muestra este signal. */
  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  readonly noLeidasCount = computed(
    () => this._notificaciones().filter((n) => !n.leido).length,
  );

  private readonly _permisoNativo = signal<NotificationPermission>(
    typeof Notification !== 'undefined' ? Notification.permission : 'default',
  );
  readonly permisoNativo = this._permisoNativo.asReadonly();

  private readonly _conectadoSse = signal<boolean>(false);
  readonly conectadoSse = this._conectadoSse.asReadonly();
  private readonly _alertaEnVivo = signal<Notificacion | null>(null);
  readonly alertaEnVivo = this._alertaEnVivo.asReadonly();

  /**
   * H1 (T16): el stream sigue al usuario actual, no al componente que lo
   * muestra. switchMap garantiza UN solo stream: al cambiar de usuario
   * (o salir) cancela el anterior -- incluso un POST /ticket todavía en
   * vuelo -- antes de abrir el del usuario nuevo.
   */
  constructor() {
    toObservable(this.auth.usuarioActual)
      .pipe(
        map((u) => u?.id ?? null),
        distinctUntilChanged(),
        switchMap((usuarioId) => {
          this._notificaciones.set([]);
          this._alertaEnVivo.set(null);
          this._error.set(null);
          if (usuarioId === null) {
            return EMPTY;
          }
          // Historial y stream en el mismo switchMap: una respuesta lenta del
          // usuario anterior se cancela, no pisa la lista del nuevo ([FE-008]).
          return merge(this.historial().pipe(ignoreElements()), this.streamConReconexion());
        }),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  /**
   * POST /ticket -> EventSource(?ticket=). El JWT nunca va en la URL (H2).
   * Si el stream se cae lo cerramos nosotros: la reconexión automática del
   * navegador reusaría un ticket ya canjeado (401). Cada reintento pide un
   * ticket nuevo -- también cubre un reinicio del backend, que pierde los
   * tickets y los streams (viven en memoria).
   */
  private streamConReconexion(): Observable<never> {
    return defer(() => this.http.post<TicketSse>(`${BASE_URL}/ticket`, {})).pipe(
      switchMap(({ ticket }) => this.abrirStream(ticket)),
      retry({ delay: () => timer(REINTENTO_MS) }),
    );
  }

  private abrirStream(ticket: string): Observable<never> {
    return new Observable<never>((suscriptor) => {
      if (typeof EventSource === 'undefined') {
        return;
      }
      const es = new EventSource(`${BASE_URL}/stream?ticket=${encodeURIComponent(ticket)}`);
      let reconexion = false;
      es.addEventListener('CONNECT', () => {
        // Tras una caída pudo llegar algo que no vimos: la lista se rehace desde el backend.
        if (reconexion) this.historial().subscribe();
        reconexion = true;
        this._conectadoSse.set(true);
      });
      es.addEventListener('ALERTA', (event: MessageEvent) => {
        try {
          this.procesarAlertaEntrante(JSON.parse(event.data));
        } catch {
          // Ignorar payloads no parseables
        }
      });
      es.onerror = () => {
        es.close();
        this._conectadoSse.set(false);
        suscriptor.error(new Error('stream SSE caído'));
      };
      return () => {
        es.close();
        this._conectadoSse.set(false);
      };
    });
  }

  private procesarAlertaEntrante(nueva: Notificacion): void {
    this._notificaciones.update((lista) => [nueva, ...lista]);
    this._alertaEnVivo.set(nueva);

    void this.mostrarNativa(nueva);
  }

  /**
   * H3 (T16): en Android `new Notification()` lanza "Illegal constructor":
   * ahí solo funciona registration.showNotification() del service worker.
   * El SW solo se registra en producción (app.config.ts); sin registro
   * (ng serve, escritorio) se cae a `new Notification`.
   */
  private async mostrarNativa(nueva: Notificacion): Promise<void> {
    if (typeof Notification === 'undefined' || Notification.permission !== 'granted') {
      return;
    }
    const opciones: NotificationOptions = { body: nueva.mensaje, icon: '/icons/icon-192x192.png', tag: `notif-${nueva.id}` };
    try {
      const registro = 'serviceWorker' in navigator ? await navigator.serviceWorker.getRegistration() : undefined;
      if (registro) {
        // ready y no el registro directo: showNotification exige un worker ACTIVO.
        await (await navigator.serviceWorker.ready).showNotification(nueva.titulo, opciones);
      } else {
        new Notification(nueva.titulo, opciones);
      }
    } catch {
      // Sin aviso nativo la alerta igual queda en la campana.
    }
  }

  solicitarPermisoNativo(): void {
    if (typeof Notification !== 'undefined') {
      Notification.requestPermission().then((permiso) => {
        this._permisoNativo.set(permiso);
      });
    }
  }

  /**
   * GET /api/notificaciones. Nunca propaga el error: corre dentro del
   * switchMap del stream (merge) y un historial caído no debe tumbar el aviso en vivo.
   */
  private historial(): Observable<Notificacion[]> {
    this._cargando.set(true);
    this._error.set(null);
    return this.http.get<Notificacion[]>(BASE_URL).pipe(
      tap((lista) => this._notificaciones.set(lista)),
      catchError((err) => {
        this._error.set(this.mensajeDe(err, 'No se pudieron cargar las notificaciones.'));
        return EMPTY;
      }),
      finalize(() => this._cargando.set(false)),
    );
  }

  /**
   * PUT /api/notificaciones/{id}/leer. Optimista: se marca al instante; si
   * el backend falla (p. ej. 404, no es para este rol) vuelve a no leída.
   * Sin `cargando`: no hay espera visible que mostrar.
   */
  marcarLeida(id: number): Observable<void> {
    this._error.set(null);
    this.fijarLeido([id], true);
    return this.http.put<void>(`${BASE_URL}/${id}/leer`, {}).pipe(
      catchError((err) => {
        this.fijarLeido([id], false);
        return this.manejarError(err, 'No se pudo marcar la notificación como leída.');
      }),
    );
  }

  // PUT /api/notificaciones/leer-todas -- si falla, vuelven a no leídas solo las que lo eran.
  marcarTodasLeidas(): Observable<void> {
    this._error.set(null);
    const eranNoLeidas = this._notificaciones().filter((n) => !n.leido).map((n) => n.id);
    this.fijarLeido(eranNoLeidas, true);
    return this.http.put<void>(`${BASE_URL}/leer-todas`, {}).pipe(
      catchError((err) => {
        this.fijarLeido(eranNoLeidas, false);
        return this.manejarError(err, 'No se pudieron marcar las notificaciones como leídas.');
      }),
    );
  }

  private fijarLeido(ids: number[], leido: boolean): void {
    const afectados = new Set(ids);
    this._notificaciones.update((lista) => lista.map((n) => (afectados.has(n.id) ? { ...n, leido } : n)));
  }

  private mensajeDe(err: HttpErrorResponse & { traducido?: ErrorTraducido }, mensajeDefecto: string): string {
    return err.traducido?.mensaje ?? mensajeDefecto;
  }

  private manejarError(err: HttpErrorResponse & { traducido?: ErrorTraducido }, mensajeDefecto: string): Observable<never> {
    this._error.set(this.mensajeDe(err, mensajeDefecto));
    return throwError(() => err);
  }
}
