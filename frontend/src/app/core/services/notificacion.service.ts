import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Notificacion } from '../models/notificacion.model';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/notificaciones`;

@Injectable({ providedIn: 'root' })
export class NotificacionService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private readonly _notificaciones = signal<Notificacion[]>([]);
  readonly notificaciones = this._notificaciones.asReadonly();

  readonly noLeidasCount = computed(
    () => this._notificaciones().filter((n) => !n.leido).length,
  );

  readonly permisoNativo = signal<NotificationPermission>(
    typeof Notification !== 'undefined' ? Notification.permission : 'default',
  );

  readonly conectadoSse = signal<boolean>(false);
  readonly alertaEnVivo = signal<Notificacion | null>(null);

  private eventSource: EventSource | null = null;
  private reintentoTimer: ReturnType<typeof setTimeout> | null = null;

  conectar(): void {
    if (typeof EventSource === 'undefined' || this.eventSource) {
      return;
    }

    const token = this.auth.obtenerToken();
    const url = `${BASE_URL}/stream${token ? '?token=' + encodeURIComponent(token) : ''}`;

    try {
      this.eventSource = new EventSource(url);

      this.eventSource.addEventListener('CONNECT', () => {
        this.conectadoSse.set(true);
      });

      this.eventSource.addEventListener('ALERTA', (event: MessageEvent) => {
        try {
          const nueva: Notificacion = JSON.parse(event.data);
          this.procesarAlertaEntrante(nueva);
        } catch {
          // Ignorar payloads no parseables
        }
      });

      this.eventSource.onerror = () => {
        this.desconectar();
        this.programarReintento();
      };
    } catch {
      this.programarReintento();
    }
  }

  private procesarAlertaEntrante(nueva: Notificacion): void {
    this._notificaciones.update((lista) => [nueva, ...lista]);
    this.alertaEnVivo.set(nueva);

    // Notificación nativa del sistema operativo / navegador
    if (
      typeof Notification !== 'undefined' &&
      this.permisoNativo() === 'granted'
    ) {
      try {
        new Notification(nueva.titulo, {
          body: nueva.mensaje,
          icon: '/favicon.ico',
        });
      } catch {
        // En navegadores móviles o contextos no seguros puede lanzar excepción silenciosa
      }
    }
  }

  solicitarPermisoNativo(): void {
    if (typeof Notification !== 'undefined') {
      Notification.requestPermission().then((permiso) => {
        this.permisoNativo.set(permiso);
      });
    }
  }

  cargarHistorial(): void {
    this.http.get<Notificacion[]>(BASE_URL).subscribe({
      next: (lista) => this._notificaciones.set(lista),
      error: () => {},
    });
  }

  marcarLeida(id: number): void {
    this._notificaciones.update((lista) =>
      lista.map((n) => (n.id === id ? { ...n, leido: true } : n)),
    );
    this.http.put(`${BASE_URL}/${id}/leer`, {}).subscribe({ error: () => {} });
  }

  marcarTodasLeidas(): void {
    this._notificaciones.update((lista) =>
      lista.map((n) => ({ ...n, leido: true })),
    );
    this.http.put(`${BASE_URL}/leer-todas`, {}).subscribe({ error: () => {} });
  }

  desconectar(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
    this.conectadoSse.set(false);
  }

  private programarReintento(): void {
    if (this.reintentoTimer) return;
    this.reintentoTimer = setTimeout(() => {
      this.reintentoTimer = null;
      if (this.auth.estaAutenticado()) {
        this.conectar();
      }
    }, 5000);
  }
}
