import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { tap } from 'rxjs/operators';
import { AbrirCajaRequest, CajaDiaria, CerrarCajaRequest, MovimientoCaja } from '../models/caja.model';
import { AuthService } from './auth.service';
import { nextId, simulate } from './mock-utils';

const MOVIMIENTOS_MOCK: Omit<MovimientoCaja, 'id' | 'cajaId'>[] = [
  { tipo: 'venta', descripcion: 'Ventas en efectivo', nota: '52 boletas', monto: 1284.5, afectaEfectivo: true },
  {
    tipo: 'ingreso',
    descripcion: 'Ingresos extra',
    nota: 'Devolución de proveedor',
    monto: 60,
    afectaEfectivo: true,
  },
  { tipo: 'egreso', descripcion: 'Egresos', nota: 'Movilidad y pago de agua', monto: -45, afectaEfectivo: true },
  {
    tipo: 'merma',
    descripcion: 'Mermas registradas',
    nota: '3 productos vencidos · no afecta efectivo',
    monto: -28.4,
    afectaEfectivo: false,
  },
];

@Injectable({ providedIn: 'root' })
export class CajaService {
  private readonly auth = inject(AuthService);

  private readonly _cajaActual = signal<CajaDiaria | null>(null);
  readonly cajaActual = this._cajaActual.asReadonly();

  private readonly _movimientos = signal<MovimientoCaja[]>([]);
  readonly movimientos = this._movimientos.asReadonly();

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  /** Suma solo los movimientos que mueven efectivo del cajón (excluye mermas, Yape y tarjeta). */
  readonly efectivoEsperado = computed(() =>
    this._movimientos()
      .filter((m) => m.afectaEfectivo)
      .reduce((acc, m) => acc + m.monto, (this._cajaActual()?.montoApertura ?? 0)),
  );

  // GET /api/caja/hoy
  obtenerCajaDeHoy(): Observable<CajaDiaria | null> {
    this._cargando.set(true);
    return simulate(this._cajaActual()).pipe(tap(() => this._cargando.set(false)));
  }

  // POST /api/caja/abrir
  abrirCaja(request: AbrirCajaRequest): Observable<CajaDiaria> {
    if (this._cajaActual()?.abierta) {
      return throwError(() => new Error('Ya existe una caja abierta hoy'));
    }
    const usuario = this.auth.usuarioActual();
    const caja: CajaDiaria = {
      id: nextId('caja'),
      fecha: new Date().toISOString().slice(0, 10),
      usuarioId: usuario?.id ?? '',
      usuarioNombre: usuario?.nombre ?? '',
      turno: request.turno,
      montoApertura: request.montoInicial,
      horaApertura: new Date().toISOString(),
      horaCierre: null,
      montoContado: null,
      diferencia: null,
      observaciones: null,
      abierta: true,
    };
    return simulate(caja, 500).pipe(
      tap(() => {
        this._cajaActual.set(caja);
        this._movimientos.set(
          MOVIMIENTOS_MOCK.map((m) => ({ ...m, id: nextId('mov'), cajaId: caja.id })),
        );
      }),
    );
  }

  // GET /api/caja/{id}/movimientos
  listarMovimientos(): Observable<MovimientoCaja[]> {
    return simulate(this._movimientos());
  }

  // POST /api/caja/cerrar
  cerrarCaja(request: CerrarCajaRequest): Observable<CajaDiaria> {
    const cajaAbierta = this._cajaActual();
    if (!cajaAbierta) {
      return throwError(() => new Error('No hay una caja abierta para cerrar'));
    }
    const cerrada: CajaDiaria = {
      ...cajaAbierta,
      horaCierre: new Date().toISOString(),
      montoContado: request.montoContado,
      diferencia: request.montoContado - this.efectivoEsperado(),
      observaciones: request.observaciones ?? null,
      abierta: false,
    };
    return simulate(cerrada, 500).pipe(tap(() => this._cajaActual.set(cerrada)));
  }
}
