export type MetodoPago = 'efectivo' | 'yape' | 'tarjeta';

/** D4: de dónde salió la línea — nunca lo elige el cajero, se detecta (Nota 2). */
export type OrigenCaptura = 'ESCANEO' | 'MANUAL' | 'BUSQUEDA';

export interface ItemVenta {
  productoId: number;
  presentacionId: number;
  nombre: string;
  presentacion: string; // etiqueta de la presentación vendida, p.ej. 'Blíster'
  precioUnitario: number;
  cantidad: number;
  origenCaptura: OrigenCaptura;
}

/**
 * Datos de la receta (D-E2-4, Tarea 20): número y CMP del médico obligatorios
 * (CMP: 1 a 6 dígitos), nombre del médico y fecha de emisión opcionales. La
 * fecha no puede ser futura (400 RECETA_INVALIDA).
 */
export interface RecetaVenta {
  numero: string;
  cmp: string;
  medicoNombre: string | null;
  fechaEmision: string | null; // yyyy-MM-dd
}

export interface Venta {
  id: number;
  fecha: string; // ISO datetime
  usuarioId: number;
  items: ItemVenta[];
  subtotal: number;
  igv: number;
  total: number;
  metodoPago: MetodoPago;
  sincronizada: boolean;
  claveIdempotencia: string;
  /** Tarea 20: la receta guardada con la venta; null si ninguna línea la necesitó. */
  receta: RecetaVenta | null;
  /** Tarea 22: se cobró sin conexión y llegó desde la cola del dispositivo. */
  vendidaSinConexion?: boolean;
  /** Tarea 22: hora del reloj del dispositivo, solo auditoría (la de la venta es `fecha`, del servidor). */
  horaDispositivo?: string | null;
  /** Tarea 22: problemas que el servidor anotó al registrarla (vacío en línea). */
  revisiones?: RevisionVenta[];
}

/** Tarea 22: tipos de revisión de una venta sin conexión (tabla revisiones_venta). */
export type TipoRevision = 'STOCK_INSUFICIENTE' | 'PRECIO_DISTINTO' | 'CAJA_CERRADA' | 'OTRO';

/** Tarea 22: VISTA para cualquiera; FALTANTE solo STOCK_INSUFICIENTE y con nota. */
export type ResolucionRevision = 'VISTA' | 'FALTANTE';

export interface RevisionVenta {
  id: number;
  ventaId: number;
  tipo: TipoRevision;
  detalle: string;
  precioCobrado: number | null;
  precioVigente: number | null;
  unidadesFaltantes: number | null;
  estado: 'PENDIENTE' | 'RESUELTA';
  resolucion: ResolucionRevision | null;
  notaResolucion: string | null;
  creadoEn: string;
  resueltaEn: string | null;
  resueltaPorNombre: string | null;
  /** Solo en GET /api/ventas/revisiones (el listado del administrador). */
  ventaFecha: string | null;
  ventaTotal: number | null;
  vendedorNombre: string | null;
}

/** Tarea 22: una línea del cuerpo de la venta. precioCobrado = el precio que vio el cajero; el servidor lo usa solo con sinConexion. */
export interface ItemNuevaVenta {
  productoId: number;
  presentacionId: number;
  cantidad: number;
  origenCaptura: OrigenCaptura;
  precioCobrado?: number;
}

/** Tarea 22: el bloque de una venta cobrada sin conexión. */
export interface SinConexionVenta {
  cajaId: number;
  horaDispositivo: string; // ISO con zona
}

/**
 * Cuerpo para registrar una venta nueva; el servidor calcula precios,
 * IGV y totales — el cliente nunca manda montos (Regla 8).
 * claveIdempotencia: UUID generado al CONFIRMAR EL CARRITO (no acá,
 * no en cada intento HTTP) — ver VentaService.
 */
export interface NuevaVentaRequest {
  claveIdempotencia: string;
  items: ItemNuevaVenta[];
  metodoPago: MetodoPago;
  /** Tarea 20: obligatoria si alguna línea es de un producto con requiereReceta; si ninguna lo es, el servidor la ignora. */
  receta?: RecetaVenta | null;
  /** Tarea 22: solo en una venta cobrada sin conexión (la manda la cola). */
  sinConexion?: SinConexionVenta;
}
