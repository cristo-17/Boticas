import { Component, DestroyRef, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from '../header/header';
import { SidebarNavComponent } from '../sidebar-nav/sidebar-nav';
import { BottomNavComponent } from '../bottom-nav/bottom-nav';
import { ConnStatusComponent } from '../../shared/components/conn-status/conn-status';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner';
import { AlertaService } from '../../core/services/alerta.service';
import { ConexionService } from '../../core/services/conexion.service';
import { ConfigService } from '../../core/services/config.service';
import { CatalogoOfflineService } from '../../core/services/catalogo-offline.service';
import { CajaService } from '../../core/services/caja.service';

const MENSAJE_BANNER = {
  sync: 'Sincronizando ventas guardadas localmente…',
  offline:
    'Sin conexión. Puedes seguir vendiendo: todo se guarda en el equipo y se sube al reconectar.',
} as const;

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    HeaderComponent,
    SidebarNavComponent,
    BottomNavComponent,
    ConnStatusComponent,
    ErrorBannerComponent,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly conexion = inject(ConexionService);
  private readonly alertaService = inject(AlertaService);
  private readonly configService = inject(ConfigService);

  readonly estadoConexion = this.conexion.estado;
  readonly errorConfig = this.configService.error;

  constructor() {
    this.alertaService.listarAlertas().subscribe();
    // Tarea 22: el catálogo para vender sin conexión queda en el dispositivo desde el login,
    // no recién al entrar a Punto de Venta (si se corta la señal antes, ya está guardado).
    inject(DestroyRef).onDestroy(inject(CatalogoOfflineService).mantenerActualizado());
    // Y la caja abierta queda conocida desde el login (la venta sin conexión necesita su id):
    // antes solo se recordaba al entrar a Caja, así que quien iba directo a Punto de Venta
    // (caja abierta en otro equipo, o datos del navegador borrados) no podía vender sin señal.
    inject(CajaService)
      .obtenerCajaDeHoy()
      .subscribe({ error: () => {} });
    // DIF-03: el Shell nace después del login. Si /api/config falló al iniciar la app, se
    // vuelve a pedir acá; mientras siga fallando, errorConfig() queda en pantalla.
    if (this.configService.config() === null) {
      this.configService.cargar().subscribe({ error: () => {} });
    }
  }

  mensajeBanner(): string {
    const estado = this.estadoConexion();
    return estado === 'online' ? '' : MENSAJE_BANNER[estado];
  }
}
