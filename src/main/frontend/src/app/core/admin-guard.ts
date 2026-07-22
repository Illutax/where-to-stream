import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthApi } from './api/auth-api';

/** Allows an admin-only route; non-admins are redirected home. Re-checks /api/me on navigation. */
export const adminGuard: CanActivateFn = () => {
  const authApi = inject(AuthApi);
  const router = inject(Router);
  return authApi.me().pipe(
    map((me) => (me.admin ? true : router.parseUrl('/'))),
    catchError(() => of(router.parseUrl('/'))),
  );
};
