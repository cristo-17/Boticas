/** Envoltura de paginación uniforme (Anexo D del backend) — la misma forma para todo listado que pueda crecer. */
export interface PaginaResponse<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
}
