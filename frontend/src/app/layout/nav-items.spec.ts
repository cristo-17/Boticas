import { MAX_ACCESOS_BARRA, NAV_ITEMS, navItemsDe, repartirBarraMovil } from './nav-items';

/** R17: una pantalla de un solo rol no aparece en el menú del otro. */
describe('navItemsDe', () => {
  it('el ADMINISTRADOR ve Catálogo y Usuarios', () => {
    const rutas = navItemsDe('ADMINISTRADOR').map((i) => i.path);
    expect(rutas).toContain('/catalogo');
    expect(rutas).toContain('/usuarios');
  });

  it('el TECNICO no ve Catálogo ni Usuarios, pero sí el resto', () => {
    const rutas = navItemsDe('TECNICO').map((i) => i.path);
    expect(rutas).not.toContain('/catalogo');
    expect(rutas).not.toContain('/usuarios');
    expect(rutas).toContain('/punto-venta');
    expect(rutas).toHaveLength(6);
  });
});

/** Barra del celular: como mucho 5 accesos; con más items, 4 + "Más". */
describe('repartirBarraMovil', () => {
  it('con 5 o menos items, todos van en la barra y no hay "Más"', () => {
    const cinco = NAV_ITEMS.slice(0, 5);
    expect(repartirBarraMovil(cinco)).toEqual({ barra: cinco, mas: [] });
  });

  it('TECNICO (6): 4 en la barra (vender, caja, dashboard, stock) y el resto en "Más"', () => {
    const { barra, mas } = repartirBarraMovil(navItemsDe('TECNICO'));
    expect(barra.map((i) => i.path)).toEqual(['/alertas', '/punto-venta', '/inventario', '/caja']);
    expect(mas.map((i) => i.path)).toEqual(['/ventas', '/merma']);
  });

  it('ADMINISTRADOR (7): Catálogo va en "Más"; la barra nunca pasa de 5 contando "Más"', () => {
    const { barra, mas } = repartirBarraMovil(navItemsDe('ADMINISTRADOR'));
    expect(barra.length + 1).toBeLessThanOrEqual(MAX_ACCESOS_BARRA);
    expect(mas.map((i) => i.path)).toContain('/catalogo');
  });

  it('un item nuevo sin prioridad (usuarios, reportes, Kardex) cae en "Más"', () => {
    const conNuevo = [...navItemsDe('ADMINISTRADOR'), { path: '/kardex', label: 'Kardex', shortLabel: 'Kardex', icon: 'bi-x' }];
    expect(repartirBarraMovil(conNuevo).mas.map((i) => i.path)).toContain('/kardex');
  });
});
