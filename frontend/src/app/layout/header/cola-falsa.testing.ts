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

