import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { CanActivateFn } from '@angular/router';

export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  const token = localStorage.getItem('devmedic_token');

  if (!token) {
    router.navigate(['/login']);
    return false;
  }
  return true;
};

export const adminGuard: CanActivateFn = () => {
  const router = inject(Router);
  const token = localStorage.getItem('devmedic_token');

  if (!token) {
    router.navigate(['/login']);
    return false;
  }

  // Décode le token JWT
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    const roles: string[] = payload?.realm_access?.roles || [];
    const hasAccess = roles.some(r =>
      ['ADMIN', 'TEAM_LEAD'].includes(r.toUpperCase())
    );

    if (!hasAccess) {
      router.navigate(['/profile']);
      return false;
    }
    return true;
  } catch {
    router.navigate(['/login']);
    return false;
  }
};

export const adminOnlyGuard: CanActivateFn = () => {
  const router = inject(Router);
  const token = localStorage.getItem('devmedic_token');

  if (!token) {
    router.navigate(['/login']);
    return false;
  }

  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    const roles: string[] = payload?.realm_access?.roles || [];
    const isAdmin = roles.some(r => r.toUpperCase() === 'ADMIN');

    if (!isAdmin) {
      router.navigate(['/profile']);
      return false;
    }
    return true;
  } catch {
    router.navigate(['/login']);
    return false;
  }
};