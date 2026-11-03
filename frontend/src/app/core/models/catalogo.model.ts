/**
 * Catálogo del administrador (Tarea 17, Bloque A). A diferencia de
 * Producto (mostrador), trae unidadNombre, el estado del producto y TODAS
 * sus presentaciones, también las desactivadas: el administrador las ve y
 * puede reactivarlas. Sin estado de vencimiento: eso es de mostrador.
 */
export interface PresentacionCatalogo {
  id: number;
  etiqueta: string;
  factorConversion: number;
  precio: number;
  activo: boolean;
}

export interface ProductoCatalogo {
  id: number;
  nombre: string;
  laboratorio: string | null;
  categoria: string;
  codigoBarras: string;
  unidadNombre: string;
  activo: boolean;
  /** Tarea 20: se vende solo con receta (número y CMP del médico al cobrar). */
  requiereReceta: boolean;
  presentaciones: PresentacionCatalogo[];
}

/**
 * id ausente = presentación nueva. Con id = una existente: se actualizan
 * etiqueta y precio (solo ventas futuras) y, si estaba desactivada, se
 * reactiva. Una existente que no viene en la lista se desactiva.
 */
export interface PresentacionRequest {
  id?: number;
  etiqueta: string;
  factorConversion: number;
  precio: number;
}

export interface ProductoRequest {
  nombre: string;
  laboratorio: string | null;
  categoria: string;
  codigoBarras: string;
  unidadNombre: string;
  /** Tarea 20: obligatorio (la base no tiene valor por defecto). Solo afecta ventas futuras. */
  requiereReceta: boolean;
  presentaciones: PresentacionRequest[];
}
