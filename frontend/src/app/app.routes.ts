import { Routes } from '@angular/router';
import { Shell } from './core/layout/shell/shell';

export const routes: Routes = [
  {
    path: '',
    component: Shell,
    children: [
      {
        path: '',
        title: 'AgentShelf',
        loadComponent: () => import('./features/home/home').then((m) => m.Home),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
