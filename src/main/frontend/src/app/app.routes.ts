import { Routes } from '@angular/router';
import { adminGuard } from './core/admin-guard';

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
    path: 'watchlist',
    loadComponent: () =>
      import('./features/watchlist-import/watchlist-import-page').then((m) => m.WatchlistImportPage),
    title: 'My Watchlist — W2S',
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
  {
    path: 'settings',
    loadComponent: () => import('./features/settings/settings-page').then((m) => m.SettingsPage),
    title: 'Settings — W2S',
  },
  {
    path: 'admin/users',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/admin-users/admin-users-page').then((m) => m.AdminUsersPage),
    title: 'Users — W2S',
  },
  { path: '**', redirectTo: '' },
];
