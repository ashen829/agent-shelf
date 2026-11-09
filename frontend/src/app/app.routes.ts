import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth-guards';
import { Shell } from './core/layout/shell/shell';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Log in · AgentShelf',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'signup',
    title: 'Sign up · AgentShelf',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/signup/signup').then((m) => m.Signup),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        title: 'Dashboard · AgentShelf',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'catalog',
        title: 'Catalog · AgentShelf',
        loadComponent: () =>
          import('./features/catalog/product-list/product-list').then((m) => m.ProductList),
      },
      {
        path: 'catalog/new',
        title: 'Add product · AgentShelf',
        loadComponent: () =>
          import('./features/catalog/product-form/product-form').then((m) => m.ProductForm),
      },
      {
        path: 'catalog/:id',
        title: 'Edit product · AgentShelf',
        loadComponent: () =>
          import('./features/catalog/product-form/product-form').then((m) => m.ProductForm),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
