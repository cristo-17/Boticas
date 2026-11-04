import { Injectable } from '@angular/core';

/**
 * Tarea 22 (docs/DISENO-OFFLINE.md §1): lo que el dispositivo guarda para
 * vender sin conexión. Dos almacenes:
 * - `cola`: una entrada por venta, con clave = claveIdempotencia;
 * - `catalogo`: la foto del catálogo de mostrador, con clave = boticaId.
 * IndexedDB en el navegador; en memoria donde no existe (Vitest/jsdom). Es
 * una clase abstracta para que los tests puedan cambiarla.
 */
export type NombreAlmacen = 'cola' | 'catalogo';

@Injectable({
  providedIn: 'root',
  useFactory: () => (typeof indexedDB !== 'undefined' ? new AlmacenIndexedDb() : new AlmacenMemoria()),
})
export abstract class AlmacenOffline {
  abstract obtener<T>(almacen: NombreAlmacen, clave: string): Promise<T | undefined>;
  abstract guardar<T>(almacen: NombreAlmacen, clave: string, valor: T): Promise<void>;
  abstract borrar(almacen: NombreAlmacen, clave: string): Promise<void>;
  abstract todos<T>(almacen: NombreAlmacen): Promise<T[]>;
}

const NOMBRE_BASE = 'boticasys';
const VERSION_BASE = 1;

export class AlmacenIndexedDb extends AlmacenOffline {
  private base: Promise<IDBDatabase> | null = null;

  constructor() {
    super();
    // Pide que el navegador no borre la cola por falta de espacio (§1). Si no lo concede, sigue igual.
    navigator.storage?.persist?.().catch(() => undefined);
  }

  obtener<T>(almacen: NombreAlmacen, clave: string): Promise<T | undefined> {
    return this.operar<T | undefined>(almacen, 'readonly', (s) => s.get(clave));
  }

  async guardar<T>(almacen: NombreAlmacen, clave: string, valor: T): Promise<void> {
    await this.operar(almacen, 'readwrite', (s) => s.put(valor, clave));
  }

  async borrar(almacen: NombreAlmacen, clave: string): Promise<void> {
    await this.operar(almacen, 'readwrite', (s) => s.delete(clave));
  }

  todos<T>(almacen: NombreAlmacen): Promise<T[]> {
    return this.operar<T[]>(almacen, 'readonly', (s) => s.getAll());
  }

  private abrir(): Promise<IDBDatabase> {
    this.base ??= new Promise((resolve, reject) => {
      const pedido = indexedDB.open(NOMBRE_BASE, VERSION_BASE);
      pedido.onupgradeneeded = () => {
        const db = pedido.result;
        for (const nombre of ['cola', 'catalogo']) {
          if (!db.objectStoreNames.contains(nombre)) db.createObjectStore(nombre);
        }
      };
      pedido.onsuccess = () => resolve(pedido.result);
      pedido.onerror = () => {
        this.base = null;
        reject(pedido.error);
      };
    });
    return this.base;
  }

  /** La promesa se resuelve al terminar la transacción (oncomplete): recién ahí la escritura está en disco. */
  private async operar<T>(almacen: NombreAlmacen, modo: IDBTransactionMode, accion: (s: IDBObjectStore) => IDBRequest): Promise<T> {
    const db = await this.abrir();
    return new Promise<T>((resolve, reject) => {
      const tx = db.transaction(almacen, modo);
      const pedido = accion(tx.objectStore(almacen));
      tx.oncomplete = () => resolve(pedido.result as T);
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  }
}

/** Para tests y navegadores sin IndexedDB. structuredClone imita que IndexedDB guarda una copia, no la referencia. */
export class AlmacenMemoria extends AlmacenOffline {
  private readonly datos = new Map<NombreAlmacen, Map<string, unknown>>();

  private mapa(almacen: NombreAlmacen): Map<string, unknown> {
    let m = this.datos.get(almacen);
    if (!m) {
      m = new Map();
      this.datos.set(almacen, m);
    }
    return m;
  }

  async obtener<T>(almacen: NombreAlmacen, clave: string): Promise<T | undefined> {
    const v = this.mapa(almacen).get(clave);
    return v === undefined ? undefined : (structuredClone(v) as T);
  }

  async guardar<T>(almacen: NombreAlmacen, clave: string, valor: T): Promise<void> {
    this.mapa(almacen).set(clave, structuredClone(valor));
  }

  async borrar(almacen: NombreAlmacen, clave: string): Promise<void> {
    this.mapa(almacen).delete(clave);
  }

  async todos<T>(almacen: NombreAlmacen): Promise<T[]> {
    return [...this.mapa(almacen).values()].map((v) => structuredClone(v) as T);
  }
}
