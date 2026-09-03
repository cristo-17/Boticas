import { Component, OnInit, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { BadgeComponent } from '../../shared/components/badge/badge';
import { CardComponent } from '../../shared/components/card/card';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton';
import {
  SummaryCardComponent,
  SummaryCardVariant,
} from '../../shared/components/summary-card/summary-card';
import { Alerta, NivelAlerta } from '../../core/models/alerta.model';
import { AlertaService } from '../../core/services/alerta.service';
import { AuthService } from '../../core/services/auth.service';

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

@Component({
  selector: 'app-alertas',
  imports: [BadgeComponent, CardComponent, SkeletonComponent, SummaryCardComponent],
  templateUrl: './alertas.html',
  styleUrl: './alertas.scss',
})
export class AlertasScreen implements OnInit {
  private readonly alertaService = inject(AlertaService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly alertas = this.alertaService.alertas;
  readonly resumen = this.alertaService.resumen;
  readonly cargando = this.alertaService.cargando;

  readonly nivelBadge = NIVEL_BADGE;
  readonly nivelLabel = NIVEL_LABEL;

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

  readonly kpis = computed<KpiVista[]>(() => {
    const r = this.resumen();
    if (!r) return [];
    return [
      {
        label: 'Ventas del día',
        valor: r.ventasHoyTexto,
        nota: r.ventasHoyNota,
        variant: 'default',
        ruta: '/punto-venta',
      },
      {
        label: 'Productos por vencer',
        valor: String(r.productosPorVencer),
        nota: r.productosPorVencerNota,
        variant: 'warn',
        ruta: '/inventario',
      },
      {
        label: 'Stock crítico',
        valor: String(r.stockCritico),
        nota: r.stockCriticoNota,
        variant: 'danger',
        ruta: '/inventario',
      },
      {
        label: 'Estado de caja',
        valor: r.cajaEstado,
        nota: r.cajaNota,
        variant: 'info',
        ruta: '/caja',
      },
    ];
  });

  ngOnInit(): void {
    this.alertaService.obtenerResumen().subscribe();
    this.alertaService.listarAlertas().subscribe();
  }

  irA(ruta: string, queryParams?: Record<string, string>): void {
    this.router.navigate([ruta], { queryParams });
  }

  accionar(alerta: Alerta): void {
    if (alerta.accionRuta === '/alertas') {
      this.alertaService.listarAlertas().subscribe();
      return;
    }
    this.irA(alerta.accionRuta, alerta.accionQueryParams);
  }
}
