export type Turno = 'Mañana' | 'Tarde' | 'Noche';

export interface Usuario {
  id: number;
  nombre: string;
  usuario: string; // login, p.ej. 'rosa.quispe'
  rol: string; // p.ej. 'TECNICO' | 'ADMINISTRADOR'
  turno: Turno;
  sede: string;
}

export interface CredencialesLogin {
  usuario: string;
  password: string;
  turno: Turno;
}

export interface LoginResponse {
  token: string;
  usuario: Usuario;
}
