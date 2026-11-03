export type Turno = 'Mañana' | 'Tarde' | 'Noche';
export type Rol = 'TECNICO' | 'ADMINISTRADOR';

/**
 * rol viaja crudo del backend ('TECNICO'|'ADMINISTRADOR'), no como
 * frase ya armada — decisión 2026-09-09 (docs/DECISIONES.md): el
 * frontend lo necesita crudo para decidir qué mostrar/ocultar por rol
 * (Tarea 12), y quien quiera el texto bonito ("Técnico"/"Administrador")
 * lo arma en el único lugar que hoy lo muestra (caja.ts). Igual
 * boticaNombre/boticaDireccion, separados en vez del "sede" compuesto
 * del mock viejo.
 */
export interface Usuario {
  id: number;
  nombre: string;
  usuario: string; // login, p.ej. 'rosa.quispe'
  rol: Rol;
  turno: Turno;
  boticaNombre: string;
  /** Opcional en la base (la botica real no la tiene): puede llegar null. */
  boticaDireccion: string | null;
}

export interface CredencialesLogin {
  usuario: string;
  password: string;
  turno: Turno;
}

/** POST /api/auth/login (Tarea 12) — envuelve el JWT junto con el usuario. */
export interface LoginResponse {
  token: string;
  usuario: Usuario;
}

/**
 * Usuario tal como lo ve y edita el administrador en /api/usuarios (Tarea 17, Bloque B).
 * Nunca incluye passwordHash.
 */
export interface UsuarioAdmin {
  id: number;
  nombre: string;
  usuario: string;
  rol: Rol;
  activo: boolean;
  intentosFallidos: number;
  bloqueadoHasta: string | null;
  bloqueado: boolean;
  tieneCajaAbierta: boolean;
  creadoEn: string;
}

export interface CrearUsuarioRequest {
  nombre: string;
  usuario: string;
  password: string;
  rol: Rol;
}

export interface ActualizarUsuarioRequest {
  nombre: string;
  rol: Rol;
  activo: boolean;
}

