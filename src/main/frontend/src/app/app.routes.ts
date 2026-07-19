import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./features/overview/overview-page').then((m) => m.OverviewPage),
    title: 'Where 2 Stream',
  },
  {
    path: 'provider/:key',
    loadComponent: () => import('./features/provider/provider-page').then((m) => m.ProviderPage),
    title: 'Provider — W2S',
  },
  {
    path: 'list',
    loadComponent: () => import('./features/change-list/change-list-page').then((m) => m.ChangeListPage),
    title: 'Change List — W2S',
  },
  {
    path: 'manage',
    loadComponent: () => import('./features/manage/manage-page').then((m) => m.ManagePage),
    title: 'Manage cache — W2S',
  },
  {
    path: 'status',
    loadComponent: () => import('./features/status/status-page').then((m) => m.StatusPage),
    title: 'Status — W2S',
  },
  { path: '**', redirectTo: '' },
];
