import { Rol } from '../core/models/usuario.model';

export interface NavItem {
  path: string;
  label: string;
  shortLabel: string;
  icon: string;
  /** R17: si está, solo esos roles ven el item. Sin roles = todos. */
  roles?: Rol[];
}

/** Items de navegación del sidebar (escritorio) y barra inferior (móvil). */
export const NAV_ITEMS: NavItem[] = [
  { path: '/alertas', label: 'Dashboard', shortLabel: 'Dashboard', icon: 'bi-speedometer2' },
  { path: '/punto-venta', label: 'Punto de venta', shortLabel: 'Vender', icon: 'bi-cart3' },
  { path: '/ventas', label: 'Historial ventas', shortLabel: 'Ventas', icon: 'bi-receipt' },
  { path: '/inventario', label: 'Inventario / Lotes', shortLabel: 'Stock', icon: 'bi-box-seam' },
  { path: '/merma', label: 'Registrar merma', shortLabel: 'Merma', icon: 'bi-trash3' },
  { path: '/caja', label: 'Caja', shortLabel: 'Caja', icon: 'bi-cash-stack' },
  { path: '/catalogo', label: 'Catálogo', shortLabel: 'Catálogo', icon: 'bi-journal-medical', roles: ['ADMINISTRADOR'] },
  { path: '/usuarios', label: 'Usuarios', shortLabel: 'Usuarios', icon: 'bi-people', roles: ['ADMINISTRADOR'] },
  { path: '/reportes', label: 'Resultados', shortLabel: 'Resultados', icon: 'bi-bar-chart-line', roles: ['ADMINISTRADOR'] },
];

/** Items que ve ese rol (sin sesión, solo los de todos). */
export function navItemsDe(rol: Rol | undefined): NavItem[] {
  return NAV_ITEMS.filter((item) => !item.roles || (rol !== undefined && item.roles.includes(rol)));
}

/** Barra inferior del celular: como mucho 5 accesos (Tarea 17). Con más items, 4 + "Más". */
export const MAX_ACCESOS_BARRA = 5;

/**
 * Los que quedan en la barra cuando no caben todos: lo de cada turno (vender,
 * caja) y lo de un vistazo (dashboard, stock). Lo demás va en "Más", también
 * lo que se agregue después (usuarios, reportes, Kardex).
 */
const PRIORIDAD_BARRA = ['/punto-venta', '/caja', '/alertas', '/inventario'];

export interface RepartoBarra {
  barra: NavItem[];
  mas: NavItem[];
}

export function repartirBarraMovil(items: NavItem[]): RepartoBarra {
  if (items.length <= MAX_ACCESOS_BARRA) {
    return { barra: items, mas: [] };
  }
  const prioridad = (item: NavItem) => {
    const i = PRIORIDAD_BARRA.indexOf(item.path);
    return i === -1 ? Number.MAX_SAFE_INTEGER : i;
  };
  const elegidos = new Set([...items].sort((a, b) => prioridad(a) - prioridad(b)).slice(0, MAX_ACCESOS_BARRA - 1));
  // En la barra conservan el orden del menú de escritorio; "Más" también.
  return { barra: items.filter((i) => elegidos.has(i)), mas: items.filter((i) => !elegidos.has(i)) };
}
