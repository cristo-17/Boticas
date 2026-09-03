import { Injectable, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { tap } from 'rxjs/operators';
import { Merma, MotivoMerma, NuevaMermaRequest } from '../models/merma.model';
import { AuthService } from './auth.service';
import { InventarioService } from './inventario.service';
import { nextId, simulate } from './mock-utils';

/** 'Robo o pérdida' y 'Otro' exigen observación — se valida también en el backend. */
export const MOTIVOS_QUE_REQUIEREN_OBSERVACION: MotivoMerma[] = ['Robo o pérdida', 'Otro'];
export const MOTIVOS_MERMA: MotivoMerma[] = ['Vencimiento', 'Rotura', 'Deterioro', 'Robo o pérdida', 'Otro'];

@Injectable({ providedIn: 'root' })
export class MermaService {
  private readonly auth = inject(AuthService);
  private readonly inventario = inject(InventarioService);

  private readonly _mermas = signal<Merma[]>([]);
  readonly mermas = this._mermas.asReadonly();

  private readonly _cargando = signal(false);
  readonly cargando = this._cargando.asReadonly();

  // GET /api/mermas?fecha=hoy
  listarMermasDelDia(): Observable<Merma[]> {
    this._cargando.set(true);
    return simulate(this._mermas()).pipe(tap(() => this._cargando.set(false)));
  }

  // POST /api/mermas
  registrarMerma(request: NuevaMermaRequest): Observable<Merma> {
    const loteEncontrado = this.inventario.obtenerLotePorId(request.loteId);
    if (!loteEncontrado) {
      return throwError(() => new Error('Lote no encontrado'));
    }
    if (request.cantidad > loteEncontrado.stock) {
      return throwError(() => new Error(`El lote solo tiene ${loteEncontrado.stock} unidades.`));
    }
    if (MOTIVOS_QUE_REQUIEREN_OBSERVACION.includes(request.motivo) && !request.observacion?.trim()) {
      return throwError(() => new Error('Este motivo requiere una observación.'));
    }
    const merma: Merma = {
      id: nextId('m'),
      loteId: request.loteId,
      productoNombre: loteEncontrado.productoNombre,
      loteCodigo: loteEncontrado.codigo,
      cantidad: request.cantidad,
      motivo: request.motivo,
      observacion: request.observacion ?? null,
      valor: request.cantidad * loteEncontrado.precioUnitario,
      usuarioId: this.auth.usuarioActual()?.id ?? '',
      fecha: new Date().toISOString(),
    };
    return simulate(merma, 500).pipe(
      tap(() => {
        this._mermas.update((all) => [merma, ...all]);
        this.inventario.descontarStock(request.loteId, request.cantidad);
      }),
    );
  }
}
