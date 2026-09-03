export type Turno = 'Mañana' | 'Tarde' | 'Noche';

export interface Usuario {
  id: string;
  nombre: string;
  usuario: string; // login, p.ej. 'rosa.quispe'
  rol: string; // p.ej. 'Técnica farmacéutica'
  turno: Turno;
  sede: string;
}

export interface CredencialesLogin {
  usuario: string;
  password: string;
  turno: Turno;
}
