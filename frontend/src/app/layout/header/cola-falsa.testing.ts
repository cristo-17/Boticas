import { computed, signal } from '@angular/core';
import { EntradaCola } from '../../core/models/offline.model';

/** Cola de prueba con la forma que leen el encabezado y el panel. */
export function colaFalsa(propias = signal<EntradaCola[]>([])) {
  return {
    propias,
    cantidadPropias: computed(() => propias().length),
    deOtrosUsuarios: signal<{ nombre: string; cantidad: number }[]>([]),
    conObservaciones: signal([]),
    enviando: signal(false),
    enviarPendientes: async () => {},
    descartarObservacion: () => {},
  };
}


/** Catálogo sin conexión de prueba con la forma que lee el panel. */
export function catalogoFalso() {
  return {
    cantidadProductos: signal<number | null>(null),
    generadoEn: signal<string | null>(null),
    actualizando: signal(false),
    error: signal<string | null>(null),
    descargar: () => ({ subscribe: () => {} }),
  };
}
