import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { BadgeComponent } from '../../shared/components/badge/badge';
import { CardComponent } from '../../shared/components/card/card';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner';
import { PaginacionComponent } from '../../shared/components/paginacion/paginacion';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton';
import {
  SummaryCardComponent,
  SummaryCardVariant,
} from '../../shared/components/summary-card/summary-card';
import { Alerta, NivelAlerta } from '../../core/models/alerta.model';
import { AlertaService } from '../../core/services/alerta.service';
import { AuthService } from '../../core/services/auth.service';
import { ConfigService } from '../../core/services/config.service';
import { formatearMoneda } from '../../core/utils/moneda.util';

interface KpiVista {
  label: string;
  valor: string;
  nota: string;
  variant: SummaryCardVariant;
  ruta: string;
}

const NIVEL_BADGE: Record<NivelAlerta, 'danger' | 'warn' | 'info'> = {
  urgente: 'danger',
  atencion: 'warn',
  informativa: 'info',
};

const NIVEL_LABEL: Record<NivelAlerta, string> = {
  urgente: 'Urgente',
  atencion: 'Atención',
  informativa: 'Informativa',
};

const TAMANO_PAGINA = 20;

const FORMATO_HORA = new Intl.DateTimeFormat('es-PE', {
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'America/Lima',
});

@Component({
  selector: 'app-alertas',
  imports: [BadgeComponent, CardComponent, ErrorBannerComponent, PaginacionComponent, SkeletonComponent, SummaryCardComponent],
  templateUrl: './alertas.html',
  styleUrl: './alertas.scss',
})
export class AlertasScreen implements OnInit {
  private readonly alertaService = inject(AlertaService);
  private readonly auth = inject(AuthService);
  private readonly config = inject(ConfigService);
  private readonly router = inject(Router);

  readonly alertas = this.alertaService.alertas;
  readonly pagina = this.alertaService.pagina;
  readonly resumen = this.alertaService.resumen;
  readonly cargando = this.alertaService.cargando;

  /** Regla de frontend (CLAUDE.md): toda pantalla que llama a un servicio muestra su error. */
  readonly error = this.alertaService.error;

  readonly nivelBadge = NIVEL_BADGE;
  readonly nivelLabel = NIVEL_LABEL;

  readonly paginaActual = signal(0);

  readonly saludo = computed(() => {
    const hora = new Date().getHours();
    const saludoHora = hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches';
    const nombre = this.auth.usuarioActual()?.nombre.split(' ')[0] ?? '';
    return nombre ? `${saludoHora}, ${nombre}` : saludoHora;
  });

  readonly fechaTexto = new Date().toLocaleDateString('es-PE', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });

  // KPIs reformados (hueco 5): el backend manda números crudos, el texto se arma acá con moneda.util/Intl.DateTimeFormat -- nunca al revés.
  readonly kpis = computed<KpiVista[]>(() => {
    const r = this.resumen();
    if (!r) return [];
    const criticoDias = this.config.config()?.vencimiento.criticoDias ?? 30;
    const signoVariacion = r.ventasHoyVariacionPct >= 0 ? '+' : '';
    return [
      {
        label: 'Ventas del día',
        valor: formatearMoneda(r.ventasHoy),
        nota: `${signoVariacion}${r.ventasHoyVariacionPct}% vs. ayer · ${r.boletasHoy} boletas`,
        variant: 'default',
        ruta: '/punto-venta',
      },
      {
        label: 'Productos por vencer',
        valor: String(r.productosPorVencer),
        nota: `${r.productosPorVencerCriticos} vencen en menos de ${criticoDias} días`,
        variant: 'warn',
        ruta: '/inventario',
      },
      {
        label: 'Stock crítico',
        valor: String(r.stockCritico),
        nota: `${r.stockAgotado} sin unidades disponibles`,
        variant: 'danger',
        ruta: '/inventario',
      },
      {
        label: 'Estado de caja',
        valor: r.cajaEstado === 'ABIERTA' ? 'Abierta' : 'Cerrada',
        // Sin "esperado" (conteo ciego, hueco 2): ese número no se expone en ninguna pantalla antes del cierre, tampoco acá.
        nota: r.cajaEstado === 'ABIERTA' && r.cajaHoraApertura ? `Desde ${FORMATO_HORA.format(new Date(r.cajaHoraApertura))}` : 'Sin turno abierto',
        variant: 'info',
        ruta: '/caja',
      },
    ];
  });

  ngOnInit(): void {
    this.alertaService.obtenerResumen().subscribe();
    this.cargar();
  }

  cargar(): void {
    this.alertaService.listarAlertas(this.paginaActual(), TAMANO_PAGINA).subscribe();
  }

  irAPagina(pagina: number): void {
    this.paginaActual.set(Math.max(0, pagina));
    this.cargar();
  }

  irA(ruta: string, queryParams?: Record<string, string>): void {
    this.router.navigate([ruta], { queryParams });
  }

  accionar(alerta: Alerta): void {
    this.irA(alerta.accionRuta, alerta.accionQueryParams);
  }
}
