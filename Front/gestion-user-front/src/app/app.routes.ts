import { Routes } from '@angular/router';
import { authGuard, adminGuard, adminOnlyGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then(m => m.Login)
  },
  {
    path: 'callback',
    loadComponent: () => import('./pages/callback/callback').then(m => m.Callback)
  },
  {
    path: 'dashboard',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/dashboard/dashboard').then(m => m.Dashboard)
  },
  {
    path: 'users',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/users/users').then(m => m.Users)
  },
  {
    path: 'roles',
    canActivate: [adminOnlyGuard],
    loadComponent: () => import('./pages/roles/roles').then(m => m.Roles)
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/profile/profile').then(m => m.Profile)
  },
  {
    path: 'git',
    children: [
      {
        path: 'repositories',
        canActivate: [authGuard],
        loadComponent: () => import('./pages/git/repositories/repositories').then(m => m.Repositories)
      },
      {
        path: 'branches',
        canActivate: [authGuard],
        loadComponent: () => import('./pages/git/branches/branches').then(m => m.Branches)
      },
      {
        path: 'tags',
        canActivate: [authGuard],
        loadComponent: () => import('./pages/git/tags/tags').then(m => m.Tags)
      },
       {
        path: 'commit',
        canActivate: [authGuard],
        loadComponent: () => import('./pages/git/commit/commit').then(m => m.Commit)
      },
    ]
  }
];