import { PaginaResponse } from './pagina.model';

/**
 * Kardex por producto (Tarea 20, D-E2-5). Se calcula en el servidor desde
 * movimientos_stock; el saldo acumulado sale de SQL (SUM … OVER), el cliente
 * nunca suma (R19).
 *
 * - SALDO_ANTERIOR: solo con "desde", primera fila; cantidad null.
 * - ENTRADA: el lote al registrarse. entradaInicial = la completó la migración V10.
 * - SALIDA_RECETA / SALIDA_LIBRE: por línea — RECETA si el producto requiere
 *   receta y la venta la tiene guardada.
 * - MERMA.
 */
export type TipoKardex = 'SALDO_ANTERIOR' | 'ENTRADA' | 'SALIDA_LIBRE' | 'SALIDA_RECETA' | 'MERMA';

export interface FilaKardex {
  id: number | null;
  fecha: string; // ISO 8601 con offset
  tipo: TipoKardex;
  entradaInicial: boolean;
  /** Con signo: salidas negativas. null en SALDO_ANTERIOR. */
  cantidad: number | null;
  saldo: number;
  loteId: number | null;
  loteCodigo: string | null;
  ventaId: number | null;
  mermaId: number | null;
  recetaNumero: string | null;
  usuario: string | null;
}

/** Totales del rango, calculados en el servidor. stockActual = suma del stock de los lotes hoy. */
export interface ResumenKardex {
  saldoInicial: number;
  entradas: number;
  salidas: number;
  saldoFinal: number;
  stockActual: number;
  movimientos: number;
}

export interface Kardex {
  productoId: number;
  productoNombre: string;
  unidadNombre: string;
  requiereReceta: boolean;
  activo: boolean;
  desde: string | null;
  hasta: string | null;
  resumen: ResumenKardex;
  movimientos: PaginaResponse<FilaKardex>;
}
