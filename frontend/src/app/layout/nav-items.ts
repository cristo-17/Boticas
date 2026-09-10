export interface NavItem {
  path: string;
  label: string;
  shortLabel: string;
  icon: string;
}

/** Items de navegación del sidebar (escritorio) y barra inferior (móvil). */
export const NAV_ITEMS: NavItem[] = [
  { path: '/alertas', label: 'Dashboard', shortLabel: 'Dashboard', icon: 'bi-speedometer2' },
  { path: '/punto-venta', label: 'Punto de venta', shortLabel: 'Vender', icon: 'bi-cart3' },
  { path: '/ventas', label: 'Historial ventas', shortLabel: 'Ventas', icon: 'bi-receipt' },
  { path: '/inventario', label: 'Inventario / Lotes', shortLabel: 'Stock', icon: 'bi-box-seam' },
  { path: '/merma', label: 'Registrar merma', shortLabel: 'Merma', icon: 'bi-trash3' },
  { path: '/caja', label: 'Caja', shortLabel: 'Caja', icon: 'bi-cash-stack' },
];
