import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, from } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { AlmacenOffline } from '../offline/almacen-offline';
import { CatalogoOffline, ProductoOffline } from '../models/offline.model';
import { Producto } from '../models/producto.model';
import { AuthService } from './auth.service';
import { fechaLimaIso } from '../utils/fecha-lima.util';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/productos`;
const LIMITE_BUSQUEDA = 20;
/** Entre una foto y otra, mientras hay conexión (§1). */
export const REFRESCO_CATALOGO_MS = 15 * 60_000;

/**
 * Tarea 22 (docs/DISENO-OFFLINE.md §1): la foto del catálogo de mostrador
 * para vender sin conexión. Se baja entera (≈ 38 KB con el catálogo demo más
 * grande) al iniciar sesión, al abrir la app con conexión, después de vaciar
 * la cola y cada 15 minutos. Busca con la misma regla que el servidor
 * (nombre, laboratorio o código, hasta 20) y calcula el stock vendible del
 * día con los lotes de la foto.
 */
@Injectable({ providedIn: 'root' })
export class CatalogoOfflineService {
  private readonly http = inject(HttpClient);
  private readonly almacen = inject(AlmacenOffline);
  private readonly auth = inject(AuthService);

  private readonly _catalogo = signal<CatalogoOffline | null>(null);
  /** No es una pantalla: lo leen ProductoService (búsqueda sin conexión) y el aviso de stock del POS. */
  readonly catalogo = this._catalogo.asReadonly();

  private ultimaDescarga = 0;

  // GET /api/productos/catalogo-offline (TECNICO y ADMINISTRADOR) — Producto de mostrador + lotesVendibles.
  descargar(): Observable<CatalogoOffline> {
    return this.http.get<CatalogoOffline>(`${BASE_URL}/catalogo-offline`).pipe(
      switchMap((foto) =>
        from(this.almacen.guardar('catalogo', String(foto.boticaId), foto)).pipe(map(() => foto)),
      ),
      map((foto) => {
        this._catalogo.set(foto);
        this.ultimaDescarga = Date.now();
        return foto;
      }),
    );
  }

  /** La baja si la última tiene más de 15 minutos (o nunca se bajó). Sin error hacia afuera: la foto vieja sigue sirviendo. */
  refrescarSiHaceFalta(ahora = Date.now()): void {
    if (ahora - this.ultimaDescarga < REFRESCO_CATALOGO_MS) return;
    this.descargar().subscribe({ error: () => {} });
  }

  /** Lee la foto guardada de la botica de la sesión (al abrir la app sin conexión). */
  async cargarGuardado(): Promise<CatalogoOffline | null> {
    const boticaId = this.auth.claims()?.boticaId;
    if (boticaId == null) return null;
    const foto = (await this.almacen.obtener<CatalogoOffline>('catalogo', String(boticaId))) ?? null;
    if (foto && !this._catalogo()) this._catalogo.set(foto);
    return foto;
  }

  /** Misma regla que GET /api/productos?buscar= : nombre, laboratorio o código, sin distinguir mayúsculas, hasta 20, por nombre. */
  buscar(query: string): Producto[] {
    const texto = query.trim().toLocaleLowerCase('es');
    const productos = this._catalogo()?.productos ?? [];
    return productos
      .filter(
        (p) =>
          texto === '' ||
          p.nombre.toLocaleLowerCase('es').includes(texto) ||
          (p.laboratorio ?? '').toLocaleLowerCase('es').includes(texto) ||
          (p.codigoBarras ?? '').toLocaleLowerCase('es').includes(texto),
      )
      .slice(0, LIMITE_BUSQUEDA);
  }

  porCodigo(codigo: string): ProductoOffline | null {
    return this._catalogo()?.productos.find((p) => p.codigoBarras === codigo) ?? null;
  }

  /** Stock vendible de hoy (Lima) según la foto: lotes con fechaVencimiento >= hoy, la regla de FEFO. */
  stockVendible(productoId: number, hoy = fechaLimaIso()): number | null {
    const producto = this._catalogo()?.productos.find((p) => p.id === productoId);
    if (!producto) return null;
    return producto.lotesVendibles.filter((l) => l.fechaVencimiento >= hoy).reduce((acc, l) => acc + l.stock, 0);
  }
}
