import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { Alerta, ResumenDashboard } from '../models/alerta.model';
import { simulate } from './mock-utils';

const RESUMEN_MOCK: ResumenDashboard = {
  ventasHoyTexto: 'S/ 2 964.50',
  ventasHoyNota: '+12% vs. ayer · 64 boletas',
  productosPorVencer: 14,
  productosPorVencerNota: '5 vencen en menos de 30 días',
  stockCritico: 6,
  stockCriticoNota: '2 sin unidades disponibles',
  cajaEstado: 'Abierta',
  cajaNota: 'Desde 08:02 · esperado S/ 1 449.50',
};

const ALERTAS_MOCK: Alerta[] = [
  {
    id: 'a1',
    nivel: 'urgente',
    titulo: 'Omeprazol 20 mg · lote L-2311D vencido',
    cuerpo: '7 unidades vencieron el 15/08. Retíralas del anaquel B-2 y regístralas como merma.',
    monto: 'S/ 80.50',
    accionLabel: 'Retirar lote',
    accionRuta: '/inventario',
    accionQueryParams: { vencimiento: 'critico' },
  },
  {
    id: 'a2',
    nivel: 'urgente',
    titulo: 'Ibuprofeno 400 mg vence en 18 días',
    cuerpo: '12 cajas en A-4. Aplica descuento de rotación o devuelve al proveedor esta semana.',
    monto: 'S/ 180.00',
    accionLabel: 'Ver lote',
    accionRuta: '/inventario',
    accionQueryParams: { vencimiento: 'critico' },
  },
  {
    id: 'a3',
    nivel: 'atencion',
    titulo: '6 productos en stock crítico',
    cuerpo: 'Amoxicilina, Clotrimazol y 4 más por debajo del mínimo del turno.',
    monto: null,
    accionLabel: 'Ver inventario',
    accionRuta: '/inventario',
    accionQueryParams: { soloStockBajo: 'true' },
  },
  {
    id: 'a4',
    nivel: 'atencion',
    titulo: 'Caja sin cerrar del turno mañana',
    cuerpo: 'Rosa Quispe abrió a las 08:02 y no registró el cierre. Cuadra antes de las 22:00.',
    monto: 'S/ 1 449.50',
    accionLabel: 'Cerrar caja',
    accionRuta: '/caja',
    accionQueryParams: { tab: 'cierre' },
  },
  {
    id: 'a5',
    nivel: 'informativa',
    titulo: '12 ventas pendientes de sincronizar',
    cuerpo: 'Se guardaron sin conexión. Se subirán solas al recuperar señal.',
    monto: null,
    accionLabel: 'Sincronizar ahora',
    accionRuta: '/alertas',
  },
];

@Injectable({ providedIn: 'root' })
export class AlertaService {
  private readonly _alertas = signal<Alerta[]>([]);
  readonly alertas = this._alertas.asReadonly();

  private readonly _resumen = signal<ResumenDashboard | null>(null);
  readonly resumen = this._resumen.asReadonly();

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  // GET /api/dashboard/resumen
  obtenerResumen(): Observable<ResumenDashboard> {
    this._cargando.set(true);
    return simulate(RESUMEN_MOCK).pipe(
      tap((data) => {
        this._resumen.set(data);
        this._cargando.set(false);
      }),
    );
  }

  // GET /api/alertas
  listarAlertas(): Observable<Alerta[]> {
    return simulate(ALERTAS_MOCK).pipe(tap((data) => this._alertas.set(data)));
  }
}
