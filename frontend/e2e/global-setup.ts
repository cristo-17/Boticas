import { generarVideoCamara } from './camara-falsa';

/** Corre antes de lanzar cualquier navegador: el proyecto "camara" necesita el .y4m ya escrito. */
export default function globalSetup(): void {
  generarVideoCamara();
}
