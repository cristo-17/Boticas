import { HttpClient } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, from, throwError } from 'rxjs';
import { catchError, finalize, map, switchMap, tap } from 'rxjs/operators';
import { ErrorTraducido } from '../interceptors/error.interceptor';
import { AlmacenOffline } from '../offline/almacen-offline';
import { CatalogoOffline, ProductoOffline } from '../models/offline.model';
import { Producto } from '../models/producto.model';
import { AuthService } from './auth.service';
import { ConexionService } from './conexion.service';
import { fechaLimaIso } from '../utils/fecha-lima.util';
import { esFalloDeRed } from '../utils/red.util';
import { environment } from '../../../environments/environment';

const BASE_URL = `${environment.apiUrl}/productos`;
const LIMITE_BUSQUEDA = 20;
/** Entre una foto y otra, mientras hay conexión (§1). */
export const REFRESCO_CATALOGO_MS = 15 * 60_000;
/** Cada cuánto se mira si ya toca renovarla (mirar es gratis; bajar, no). */
export const REVISION_CATALOGO_MS = 60_000;

/**
 * Tarea 22 (docs/DISENO-OFFLINE.md §1): la foto del catálogo de mostrador
 * para vender sin conexión. Se baja entera (≈ 38 KB con el catálogo demo más
 * grande) al iniciar sesión o abrir la app con sesión (Shell), al entrar a
 * Punto de Venta, después de vaciar la cola, cada 15 minutos con conexión y
 * con "Actualizar ahora" del panel de conexión. Busca con la misma regla que el servidor
 * (nombre, laboratorio o código, hasta 20) y calcula el stock vendible del
 * día con los lotes de la foto.
 */
@Injectable({ providedIn: 'root' })
export class CatalogoOfflineService {
  private readonly http = inject(HttpClient);
  private readonly almacen = inject(AlmacenOffline);
  private readonly auth = inject(AuthService);
  private readonly conexion = inject(ConexionService);

  private readonly _catalogo = signal<CatalogoOffline | null>(null);
  /** No es una pantalla: lo leen ProductoService (búsqueda sin conexión) y el aviso de stock del POS. */
  readonly catalogo = this._catalogo.asReadonly();
  /** Panel de conexión: "N productos · actualizado hace X min" (generadoEn es la hora del servidor al armar la foto). */
  readonly cantidadProductos = computed(() => this._catalogo()?.productos.length ?? null);
  readonly generadoEn = computed(() => this._catalogo()?.generadoEn ?? null);

  private readonly _actualizando = signal(false);
  readonly actualizando = this._actualizando.asReadonly();
  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  private ultimaDescarga = 0;

  // GET /api/productos/catalogo-offline (TECNICO y ADMINISTRADOR) — Producto de mostrador + lotesVendibles.
  descargar(): Observable<CatalogoOffline> {
    this._actualizando.set(true);
    this._error.set(null);
    return this.http.get<CatalogoOffline>(`${BASE_URL}/catalogo-offline`).pipe(
      switchMap((foto) =>
        from(this.almacen.guardar('catalogo', String(foto.boticaId), foto)).pipe(map(() => foto)),
      ),
      tap((foto) => {
        this._catalogo.set(foto);
        this.ultimaDescarga = Date.now();
      }),
      catchError((err: HttpErrorResponse & { traducido?: ErrorTraducido }) => {
        // La foto vieja (si hay) sigue sirviendo para vender: solo se avisa que no se pudo renovar.
        this._error.set(
          esFalloDeRed(err)
            ? 'No se pudo actualizar el catálogo: sin conexión con el servidor.'
            : (err?.traducido?.mensaje ?? 'No se pudo actualizar el catálogo.'),
        );
        return throwError(() => err);
      }),
      finalize(() => this._actualizando.set(false)),
    );
  }

  /** La baja si la última tiene más de 15 minutos (o nunca se bajó). Sin error hacia afuera: la foto vieja sigue sirviendo. */
  refrescarSiHaceFalta(ahora = Date.now()): void {
    if (this._actualizando() || ahora - this.ultimaDescarga < REFRESCO_CATALOGO_MS) return;
    this.descargar().subscribe({ error: () => {} });
  }

  /**
   * Lo llama el Shell, que nace después del login (o al abrir la app con
   * sesión): lee la foto guardada y la renueva si hay conexión; después, cada
   * 15 minutos con conexión y al volver la señal. Devuelve cómo detenerlo
   * (al cerrar sesión el Shell se destruye).
   */
  mantenerActualizado(): () => void {
    const renovar = () => {
      if (this.conexion.enLinea()) this.refrescarSiHaceFalta();
    };
    void this.cargarGuardado().then(renovar);
    const intervalo = setInterval(renovar, REVISION_CATALOGO_MS);
    const sub = this.conexion.reconectado$.subscribe(renovar);
    return () => {
      clearInterval(intervalo);
      sub.unsubscribe();
    };
  }

  /** Lee la foto guardada de la botica de la sesión (al abrir la app sin conexión). */
  async cargarGuardado(): Promise<CatalogoOffline | null> {
    const boticaId = this.auth.claims()?.boticaId;
    if (boticaId == null) return null;
    const foto = (await this.almacen.obtener<CatalogoOffline>('catalogo', String(boticaId))) ?? null;
    // Otra botica en el mismo equipo: la foto en memoria (de la sesión anterior) no le sirve.
    if (this._catalogo()?.boticaId !== boticaId) this._catalogo.set(foto);
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
