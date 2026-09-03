export interface NavItem {
  path: string;
  label: string;
  shortLabel: string;
}

/** Items de navegación del sidebar (escritorio) y barra inferior (móvil). */
export const NAV_ITEMS: NavItem[] = [
  { path: '/alertas', label: 'Alertas', shortLabel: 'Alertas' },
  { path: '/punto-venta', label: 'Punto de venta', shortLabel: 'Vender' },
  { path: '/inventario', label: 'Inventario / Lotes', shortLabel: 'Stock' },
  { path: '/merma', label: 'Registrar merma', shortLabel: 'Merma' },
  { path: '/caja', label: 'Caja', shortLabel: 'Caja' },
];
