import { Routes } from '@angular/router';
import { Shell } from './layout/shell/shell';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.LoginScreen),
  },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      {
        path: 'alertas',
        loadComponent: () => import('./features/alertas/alertas').then((m) => m.AlertasScreen),
      },
      {
        path: 'punto-venta',
        loadComponent: () =>
          import('./features/punto-venta/punto-venta').then((m) => m.PuntoVentaScreen),
      },
      {
        path: 'ventas',
        loadComponent: () =>
          import('./features/ventas/historial-ventas').then((m) => m.HistorialVentasScreen),
      },
      {
        path: 'inventario',
        loadComponent: () =>
          import('./features/inventario/inventario').then((m) => m.InventarioScreen),
      },
      {
        path: 'merma',
        loadComponent: () => import('./features/merma/merma').then((m) => m.MermaScreen),
      },
      {
        path: 'caja',
        loadComponent: () => import('./features/caja/caja').then((m) => m.CajaScreen),
      },
    ],
  },
  { path: '**', redirectTo: 'alertas' },
];
